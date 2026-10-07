package ai.univs.face.application.service;

import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.FaceMatch;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.domain.repository.FaceMatchRepository;
import ai.univs.face.infrastructure.repository.FaceLivenessJpaRepository;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.CustomFaceException;
import ai.univs.face.shared.exception.InvalidFaceImageException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import ai.univs.face.shared.exception.PoolExhaustion;
import ai.univs.face.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이력(face_history·face_match)을 <b>짧은 트랜잭션</b>으로 나눠 커밋한다 (UG-358, scaling P0-1).
 *
 * <p>예전 유스케이스는 메서드 전체가 트랜잭션이라 fxp 추출(이미지 업로드)과 match 호출이 끝날 때까지 DB 커넥션을
 * 쥐었다. 동시 32 에서 face 풀(10)이 active 10/10, 대기 50여 건으로 막혔다(UP-6 측정). 이제 유스케이스는 트랜잭션이
 * 없고, 이 빈의 메서드만 각자 짧게 커밋한다.
 * <ol>
 *   <li>{@link #start} — 요청 이력을 먼저 커밋한다. 원격 호출이 어떻게 끝나도 행이 남는다.
 *   <li>원격 호출 — 트랜잭션 없이 한다.
 *   <li>{@link #finish} — 결과를 커밋한다. 예외로 끝나면 {@link #recordFailure} 가 사유를 채워 남긴다.
 * </ol>
 *
 * <p><b>전파는 REQUIRED 다(REQUIRES_NEW 아님).</b> 호출자가 트랜잭션 없이 부르는 것이 전제다. 바깥 트랜잭션 안에서
 * REQUIRES_NEW 로 열면 요청 하나가 커넥션 둘을 쥐어, 풀 크기만큼의 동시 요청에서 교착한다(gate UG-336). 유스케이스
 * (1단계 읽기 6개, 2단계 등록·수정·삭제·추출·라이브니스)에 {@code @Transactional} 이 다시 붙지 않는지는
 * {@code UseCaseTransactionGuardTest} 가 막는다.
 *
 * <p>호출 사이의 {@link FaceHistory} 는 준영속이다(open-in-view 끔). {@code save} 가 merge 로 갱신한다 — merge 의
 * 사전 조회가 PK 한 번이 되도록 FaceHistory 의 역방향 EAGER 연관을 지웠다(반박 리뷰 H1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaceHistoryRecorder {

    private final FaceHistoryRepository faceHistoryRepository;
    private final FaceMatchRepository faceMatchRepository;
    private final FaceLivenessJpaRepository faceLivenessRepository;
    private final TransactionTemplate transactionTemplate;

    /** 요청 이력을 커밋하고 저장된 것을 돌려준다 — 이후 단계는 돌려받은 것을 쓴다. */
    @Transactional
    public FaceHistory start(FaceHistory faceHistory) {
        return faceHistoryRepository.save(faceHistory);
    }

    /**
     * 결과를 한 번에 커밋한다 — 매칭 결과 행(있으면)과 이력의 성공·실패 상태.
     *
     * <p>임계치 미달(NOT_MATCH)도 여기로 온다. 매칭 결과 행은 성공·미달 모두 남기는 것이 예전 동작이다.
     *
     * <p>{@code @Transactional} 대신 {@link TransactionTemplate} 을 쓰는 이유: 커밋 실패까지 여기서 받아야 한다.
     * 선언형이면 커밋은 프록시가 메서드 밖에서 하므로 잡을 수 없다. 실패하면 이력을 「결과 미기록(result=false) +
     * INTERNAL_SERVER_ERROR」로 바꿔 두고 던진다 — 유스케이스의 {@link #recordFailure} 가 그 상태로 남긴다. 그러지 않으면
     * 「성공인데 사유 없음」이나 「NOT_MATCH 인데 결과 행 없음」처럼 섞인 상태가 남는다(반박 리뷰 L1).
     *
     * <p><b>쓰기(등록·수정·삭제)의 결과 커밋 실패</b>는 match 에 이미 반영된 뒤다 (UG-358 2단계, UG-338). 상태를 되돌릴 때
     * faceId 는 지우지 않는다 — 등록이면 match 가 준 id 가 실패 이력에 남아 운영자가 고아를 찾을 수 있다. 풀 고갈이면
     * {@link #recordFailure} 가 저장을 건너뛰므로, 매처가 발급한 id(v1)는 행에 남지 않는다(호출자가 발급한 v2 id 는 시작
     * 행에 이미 있다) — 그래서 여기서 로그로도 남긴다.
     */
    public void finish(FaceHistory faceHistory, FaceMatch faceMatch) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                savePendingLiveness(faceHistory);
                if (faceMatch != null) faceMatchRepository.save(faceMatch);
                faceHistoryRepository.save(faceHistory);
            });
            faceHistory.clearPendingLiveness();
        } catch (RuntimeException e) {
            faceHistory.failUnrecorded(ErrorType.INTERNAL_SERVER_ERROR.name(), faceHistory.getModifiedBy());
            if (isRemoteWrite(faceHistory.getType())) {
                // 스택트레이스는 예외 핸들러가 남긴다. 여기서는 match 와 face 가 갈라진 사실과 찾을 열쇠만 남긴다.
                log.error("match 에 반영된 뒤 결과를 커밋하지 못했다 — type={}, faceId={}, transactionUuid={}, cause={}",
                        faceHistory.getType(), faceHistory.getFaceId(), faceHistory.getTransactionUuid(), e.toString());
            }
            throw e;
        }
    }

    private static boolean isRemoteWrite(ActionType type) {
        return type == ActionType.ADD || type == ActionType.UPDATE || type == ActionType.REMOVE;
    }

    /**
     * 예외로 끝난 요청의 이력을 남긴다 — 실패 사유가 비어 있으면 채운다 (UG-280 교훈).
     *
     * <p>예전에는 fxp·match 의 5xx·타임아웃·연결 실패({@code UpstreamCallException}), 빈 응답의 NPE 같은 예외가
     * noRollbackFor 에 없어 <b>이력 행 자체가 롤백으로 사라졌다.</b> 이제 시작 행이 이미 커밋돼 있으므로 사유를
     * 채워 남긴다. 사유를 이미 적은 실패(FACE_NOT_FOUND, 라이브니스, match 의 오류 응답, 결과 커밋 실패)는 그대로 둔다.
     *
     * <p>이 기록이 실패해도(DB 장애) 원래 예외를 가리지 않는다 — 억제된 예외로 붙이고 돌아간다.
     */
    public void recordFailure(FaceHistory faceHistory, RuntimeException cause, String clientId) {
        if (PoolExhaustion.find(cause).isPresent()) {
            // UG-359 반박 리뷰 L1: 우리 풀이 고갈돼 실패했다. 같은 풀로 저장을 또 시도하면 connection-timeout 을 한 번 더
            // 기다려 503 이 그만큼 늦게 나간다 — 대기 시간을 2~3초로 줄여도 4~6초가 되어 gate 의 Feign readTimeout(5초)에
            // 걸린다(그러면 클라이언트는 PJ-006 이 아니라 400 PJ-005 를 받는다). 행은 시작 상태(사유 없음)로 남는다.
            log.warn("풀 고갈로 실패 이력을 남기지 않는다 — transactionUuid={}", faceHistory.getTransactionUuid());
            return;
        }
        if (faceHistory.getFailureMessage() == null) {
            // CustomFeignException: fxp 가 4xx 로 거절한 경우 — 클라이언트가 받는 유형과 이력 사유를 맞춘다(2단계 반박 리뷰 L2)
            String type = cause instanceof InvalidFaceModuleException module ? module.getType()
                    : cause instanceof CustomFeignException feign && feign.getType() != null ? feign.getType()
                    : cause instanceof InvalidFaceImageException image ? image.getErrorType().name()
                    : cause instanceof CustomFaceException custom ? custom.getErrorType().name()
                    : ErrorType.INTERNAL_SERVER_ERROR.name();
            faceHistory.fail(type, clientId);
        }
        try {
            // 붙여 둔 라이브니스 결과(라이브니스 실패 -777 등)도 이력과 한 트랜잭션으로 남긴다 — 예전에도 이 경우 행이 남았다.
            transactionTemplate.executeWithoutResult(status -> {
                savePendingLiveness(faceHistory);
                faceHistoryRepository.save(faceHistory);
            });
            faceHistory.clearPendingLiveness();
        } catch (RuntimeException recordError) {
            cause.addSuppressed(recordError);
            // 2단계 델타 리뷰 L-1·L-2: 라이브니스 행 자체가 실패의 원인일 수 있다(fxp 가 NOT NULL 필드를 빠뜨림 — 이제 그
            // insert 는 match 등록 뒤 결과 커밋에서 난다). 또 결과 커밋 롤백 뒤 남은 IDENTITY id 때문에 merge 가 실패할 수도
            // 있다(Hibernate 6.6+). 어느 쪽이든 사유를 잃지 않게 이력만 한 번 더 남긴다 — 라이브니스 행은 포기한다.
            faceHistory.clearPendingLiveness();
            try {
                faceHistoryRepository.save(faceHistory);
                log.warn("라이브니스 행 없이 실패 이력만 남겼다 — transactionUuid={}", faceHistory.getTransactionUuid(), recordError);
            } catch (RuntimeException historyOnlyError) {
                log.warn("실패 이력을 남기지 못했다 — transactionUuid={}", faceHistory.getTransactionUuid(), historyOnlyError);
                cause.addSuppressed(historyOnlyError);
            }
        }
    }

    private void savePendingLiveness(FaceHistory faceHistory) {
        if (faceHistory.getPendingLiveness() != null) faceLivenessRepository.save(faceHistory.getPendingLiveness());
    }
}

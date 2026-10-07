package ai.univs.face.application.service;

import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.FaceMatch;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.domain.repository.FaceMatchRepository;
import ai.univs.face.shared.exception.CustomFaceException;
import ai.univs.face.shared.exception.InvalidFaceImageException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import ai.univs.face.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이력(face_history·face_match)을 <b>짧은 트랜잭션</b>으로 나눠 커밋한다 (UG-358, scaling P0-1).
 *
 * <p>예전 유스케이스는 메서드 전체가 트랜잭션이라 fxp 추출(이미지 업로드)과 match 호출이 끝날 때까지 DB 커넥션을
 * 쥐었다. 동시 32 에서 face 풀(10)이 active 10/10, 대기 50여 건으로 막혔다(UP-6 측정). 이제 유스케이스는 트랜잭션이
 * 없고, 이 빈의 메서드만 각자 짧게 커밋한다.
 * <ol>
 *   <li>{@link #start} — 요청 이력을 먼저 커밋한다. 원격 호출이 어떻게 끝나도 행이 남는다.
 *   <li>원격 호출 — 트랜잭션 없이 한다.
 *   <li>{@link #finish} 또는 {@link #fail} — 결과를 커밋한다.
 * </ol>
 *
 * <p><b>전파는 REQUIRED 다(REQUIRES_NEW 아님).</b> 호출자가 트랜잭션 없이 부르는 것이 전제다. 바깥 트랜잭션 안에서
 * REQUIRES_NEW 로 열면 요청 하나가 커넥션 둘을 쥐어, 풀 크기만큼의 동시 요청에서 교착한다(gate UG-336). 읽기
 * 유스케이스에 {@code @Transactional} 이 다시 붙지 않는지는 {@code ReadUseCaseTransactionGuardTest} 가 막는다.
 *
 * <p>호출 사이의 {@link FaceHistory} 는 준영속이다(open-in-view 끔). {@code save} 가 merge 로 갱신한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaceHistoryRecorder {

    private final FaceHistoryRepository faceHistoryRepository;
    private final FaceMatchRepository faceMatchRepository;

    /** 요청 이력을 커밋하고 저장된 것을 돌려준다 — 이후 단계는 돌려받은 것을 쓴다. */
    @Transactional
    public FaceHistory start(FaceHistory faceHistory) {
        return faceHistoryRepository.save(faceHistory);
    }

    /**
     * 결과를 한 번에 커밋한다 — 매칭 결과 행(있으면)과 이력의 성공·실패 상태.
     *
     * <p>임계치 미달(NOT_MATCH)도 여기로 온다. 매칭 결과 행은 성공·미달 모두 남기는 것이 예전 동작이다.
     */
    @Transactional
    public void finish(FaceHistory faceHistory, FaceMatch faceMatch) {
        if (faceMatch != null) faceMatchRepository.save(faceMatch);
        faceHistoryRepository.save(faceHistory);
    }

    /** 이미 {@link FaceHistory#fail} 로 실패 사유를 적은 이력을 커밋한다. */
    @Transactional
    public void fail(FaceHistory faceHistory) {
        faceHistoryRepository.save(faceHistory);
    }

    /**
     * 예외로 끝난 요청의 이력을 남긴다 — 실패 사유가 비어 있으면 채운다 (UG-280 교훈).
     *
     * <p>예전에는 fxp·match 의 5xx·타임아웃·연결 실패({@code UpstreamCallException}), 빈 응답의 NPE 같은 예외가
     * noRollbackFor 에 없어 <b>이력 행 자체가 롤백으로 사라졌다.</b> 이제 시작 행이 이미 커밋돼 있으므로 사유를
     * 채워 남긴다. 사유를 이미 적은 실패(FACE_NOT_FOUND, 라이브니스, match 의 오류 응답)는 그대로 둔다.
     *
     * <p>이 기록이 실패해도(DB 장애) 원래 예외를 가리지 않는다 — 억제된 예외로 붙이고 돌아간다.
     */
    public void recordFailure(FaceHistory faceHistory, RuntimeException cause, String clientId) {
        if (faceHistory.isResult()) {
            // 성공 결과를 커밋하다 실패한 경우({@link #finish} 의 DB 오류)다. 「성공 + 실패 사유」를 섞어 남기지 않는다 —
            // 행은 시작 상태(result=false, 사유 없음)로 남는다. gate HistoryRecorder 의 「전이를 못 부른 경우」와 같다.
            log.warn("성공 이력 커밋 실패 — 시작 상태로 남긴다. transactionUuid={}", faceHistory.getTransactionUuid());
            return;
        }
        if (faceHistory.getFailureMessage() == null) {
            String type = cause instanceof InvalidFaceModuleException module ? module.getType()
                    : cause instanceof InvalidFaceImageException image ? image.getErrorType().name()
                    : cause instanceof CustomFaceException custom ? custom.getErrorType().name()
                    : ErrorType.INTERNAL_SERVER_ERROR.name();
            faceHistory.fail(type, clientId);
        }
        try {
            // 자기 호출이라 이 빈의 @Transactional 은 타지 않는다. 저장소의 save 가 그 자체로 트랜잭션이라 커밋된다.
            faceHistoryRepository.save(faceHistory);
        } catch (RuntimeException recordError) {
            log.warn("실패 이력을 남기지 못했다 — transactionUuid={}", faceHistory.getTransactionUuid(), recordError);
            cause.addSuppressed(recordError);
        }
    }
}

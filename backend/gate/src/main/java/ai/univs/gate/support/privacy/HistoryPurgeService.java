package ai.univs.gate.support.privacy;

import ai.univs.gate.support.file.FileService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보존 기간이 지난 인증·특징점 이력을 지운다 (UG-282).
 *
 * <p><b>무엇이 문제였나.</b> {@code match_history} 와 {@code feature_history} 는 회수 경로가
 * 없다. 인증이 일어날 때마다 행이 쌓이고, 동의를 받은 프로젝트는 시도마다 프로브 이미지까지
 * MinIO 에 올라간다. 지우는 코드는 어디에도 없었다.
 *
 * <ul>
 *   <li><b>개인정보.</b> 얼굴·손바닥 프로브 이미지와 누가 언제 어느 결과로 인증했는지가
 *       무기한 남는다. 개인정보 보호법 제21조는 목적을 달성한 개인정보를 <b>지체 없이</b>
 *       파기하도록 한다.
 *   <li><b>저장소·성능.</b> 가장 큰 두 테이블이 단조 증가하고, 대시보드 집계(UG-282 의 인덱스
 *       작업)가 그 위에서 돈다.
 * </ul>
 *
 * <p><b>기본값이 "아무것도 하지 않음" 인 이유는 {@link ProjectDataPurgeService} 와 다르다.</b>
 * 저쪽은 "얼마나 기다렸다 지울까" 가 미정이라 꺼 뒀다. 여기는 <b>지우면 안 될 수도 있다</b>.
 * e-KYC 이력은 위탁자 쪽 법령에서 보존 의무가 걸릴 수 있다 — 금융회사라면 특정금융정보법
 * 제5조의4 의 고객확인자료(거래 관계 종료 후 5년), 전자금융거래법 시행령 제12조의 거래기록
 * (5년, 건당 1만원 이하는 1년)이다. gate 는 대개 수탁자이므로 그 기간의 주인은 고객사다.
 * 코드가 기본값을 정하면 <b>고객사의 법정 의무를 우리가 깨는</b> 형태가 된다.
 *
 * <p>그래서 {@code gate.privacy.history-purge.retention-days} 를 주지 않으면 대상 조회조차
 * 하지 않는다. 출입문 게이트처럼 금융 법령이 걸리지 않는 납품은 짧은 값을 쓸 수 있고, 금융
 * 납품은 5년(1825일) 이상을 쓴다. 판단은 계약이 한다.
 *
 * <p><b>무엇을 지우고 무엇을 남기는가.</b>
 * <ul>
 *   <li>지운다 — {@code match_history}·{@code feature_history} 행(물리 삭제), 그리고 <b>그 행만
 *       가리키는</b> 이미지. 어느 경로가 그런 것인지는 {@code match_type} 에 따라 다르고,
 *       판단은 {@link MatchHistoryPurgeTarget#ownedImagePaths()} 한 곳에 있다.
 *   <li>남긴다 — {@code biometric_feature} 와 그 원본 이미지. 등록된 사용자는 이력의 보존
 *       기간과 무관하게 살아 있다. 그쪽을 지우는 것은 {@link ProjectDataPurgeService} 다.
 *   <li>건드리지 않는다 — {@code match_type = REGISTER} 인 잔존 행. 이유는
 *       {@link HistoryPurgeRepository#findMatchHistoryToPurge} 참고.
 * </ul>
 *
 * <p><b>이미지를 먼저 지우고 행을 지운다.</b> 순서를 뒤집으면 커밋 직후 죽었을 때 아무도
 * 가리키지 않는 파일이 남고, 그 파일은 어느 경로로도 다시 찾을 수 없다 — 개인정보를 지우는
 * 것이 목적인 기능에서 가장 나쁜 실패다. 반대 방향의 중간 상태(행은 있는데 파일이 없다)는
 * 다음 실행이 정리하고, UG-303 이 이미 만드는 상태와 같다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryPurgeService {

    private final HistoryPurgeRepository historyPurgeRepository;
    private final FileService fileService;

    /**
     * 인증 이력 한 배치.
     *
     * <p>배치마다 트랜잭션을 끊는다({@code REQUIRES_NEW}). 전체를 한 트랜잭션으로 묶으면 밀린
     * 분량이 많은 첫 실행에서 대형 트랜잭션이 되고, 마지막 배치의 실패가 앞의 성공을 전부
     * 되돌린다.
     *
     * @return 지운 행 수. 0 이면 더 지울 것이 없다는 뜻이고, 호출자는 그것으로 멈춘다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeMatchHistoryBatch(LocalDateTime cutoff, int batchSize) {
        List<MatchHistoryPurgeTarget> rows =
                historyPurgeRepository.findMatchHistoryToPurge(cutoff, batchSize);
        if (rows.isEmpty()) {
            return 0;
        }

        List<Long> ids = rows.stream().map(MatchHistoryPurgeTarget::id).toList();
        rows.stream().map(MatchHistoryPurgeTarget::ownedImagePaths)
                .flatMap(List::stream)
                .forEach(this::deleteImage);

        return historyPurgeRepository.deleteMatchHistory(ids);
    }

    /** 특징점 사건 이력 한 배치. 지울 이미지가 없다 — 이유는 리포지토리 주석 참고. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeFeatureHistoryBatch(LocalDateTime cutoff, int batchSize) {
        List<Long> ids = historyPurgeRepository.findFeatureHistoryToPurge(cutoff, batchSize);
        if (ids.isEmpty()) {
            return 0;
        }
        return historyPurgeRepository.deleteFeatureHistory(ids);
    }

    /**
     * 이 이력 행만 가리키는 이미지를 지운다.
     *
     * <p>어느 경로가 그런 것인지는 {@link MatchHistoryPurgeTarget#ownedImagePaths()} 가 고른다 —
     * 이 메서드는 받은 것을 지울 뿐이다.
     *
     * <p>실패해도 행 삭제는 진행한다. 여기서 멈추면 저장소 한 번의 장애가 이력 정리 전체를
     * 영구히 막는다 — 그 편이 파일 하나가 남는 것보다 나쁘다. ({@link ProjectDataPurgeService}
     * 와 같은 판단이다.)
     */
    private void deleteImage(String path) {
        try {
            fileService.delete(path);
        } catch (RuntimeException e) {
            log.warn("이력 이미지 삭제 실패 — 행은 그대로 지운다. path={}, 원인={}",
                    path, e.getClass().getSimpleName());
        }
    }
}

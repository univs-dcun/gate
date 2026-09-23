package ai.univs.gate.support.privacy;

import ai.univs.gate.support.file.FileService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
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
 * 저장소에 올라간다({@code FileUtil} 이 {@code ${file.root-path}} 아래 로컬 경로에 쓴다 —
 * MinIO 가 아니다). 지우는 코드는 어디에도 없었다.
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
 * <p><b>무엇을 지우고 무엇을 남기는가.</b> 규칙은 한 문장이다 —
 * <b>지우는 행이 들고 있던 이미지는, 살아 있는 {@code biometric_feature} 가 가리키지 않는 한
 * 함께 지운다.</b>
 *
 * <ul>
 *   <li>지운다 — {@code match_history}·{@code feature_history} 행(물리 삭제, 종류를 가리지
 *       않는다), 그리고 그 행의 이미지 중 아무도 가리키지 않는 것.
 *   <li>남긴다 — {@code biometric_feature} 와 그 원본 이미지. 등록된 사용자는 이력의 보존
 *       기간과 무관하게 살아 있다. 그쪽을 지우는 것은 {@link ProjectDataPurgeService} 다.
 * </ul>
 *
 * <p><b>왜 참조로 정하는가 — 타입으로 가르려다 두 번 틀렸다.</b> 이력 행의
 * {@code feature_image_path} 는 <b>대개</b> 등록된 특징점의 경로를 복사한 값이지만, 어떤 행에서는
 * 그 요청에서 올린 신분증 이미지다. 그 구분은 {@code match_type} 으로 표현되지 않는다 — 레거시
 * {@code VERIFY} 는 by-id 행(공유)과 by-image 행(전용)이 <b>같은 값</b>을 쓴다(2026-05 ec9e6d4
 * 이전). 자세한 경위는 {@link MatchHistoryPurgeTarget} 참고.
 *
 * <p>참조로 정하면 타입을 몰라도 된다. 프로브 이미지와 신분증 이미지는 어떤 특징점도 가리키지
 * 않으므로 지워지고, 등록 사진은 특징점이 가리키므로 남는다. 앞으로 추가될 매칭 API 도 같은
 * 규칙에 자동으로 들어온다.
 *
 * <p><b>남는 틈 하나.</b> 제품 API 로 삭제된 특징점은 해당하지 않는다 —
 * {@code DeleteFaceFeatureUseCase} 는 {@code is_deleted} 만 찍고 행도 파일도 남기며, 참조
 * 검사가 {@code is_deleted} 를 보지 않으므로 그 경로는 지켜진다. 실제로 틈이 생기는 경우는
 * {@link ProjectDataPurgeService} 가 <b>파일 삭제에 실패한 뒤 특징점 행은 지운</b> 상태
 * 하나다. 그때 그 파일을 가리키는 다른 이력 행 — <b>cutoff 보다 최신이라 아직 보존 기간 안에
 * 있는 행</b> — 의 썸네일이 깨진다. 이미 특징점이 사라진 사용자의 사진이므로 지우는 쪽이 이
 * 기능의 목적에 맞다고 보고 받아들인다 (3차 반박 리뷰가 앞의 서술이 발생 조건과 영향 범위를
 * 둘 다 틀리게 적었다고 지적했다).
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
        return purgeBatch(
                historyPurgeRepository.findMatchHistoryToPurge(cutoff, batchSize),
                historyPurgeRepository::deleteMatchHistory);
    }

    /** 특징점 사건 이력 한 배치. 인증 이력과 <b>같은 규칙</b>을 거친다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeFeatureHistoryBatch(LocalDateTime cutoff, int batchSize) {
        return purgeBatch(
                historyPurgeRepository.findFeatureHistoryToPurge(cutoff, batchSize),
                historyPurgeRepository::deleteFeatureHistory);
    }

    /**
     * 한 배치를 처리한다 — 두 이력이 같은 코드를 지난다.
     *
     * <p>갈라 두면 한쪽에만 참조 검사를 넣는 실수가 가능해진다. 규칙이 하나이므로 구현도
     * 하나여야 한다.
     *
     * <p>파일을 먼저 지우고 행을 지운다. 참조 검사는 {@code biometric_feature} 만 보므로 행
     * 삭제 순서에 영향을 받지 않는다.
     */
    private int purgeBatch(List<MatchHistoryPurgeTarget> rows, ToIntFunction<List<Long>> delete) {
        if (rows.isEmpty()) {
            return 0;
        }

        Set<String> candidates = rows.stream()
                .map(MatchHistoryPurgeTarget::candidateImagePaths)
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        Set<String> keep = historyPurgeRepository.findPathsStillReferencedByFeatures(candidates);
        candidates.stream().filter(p -> !keep.contains(p)).forEach(this::deleteImage);

        return delete.applyAsInt(rows.stream().map(MatchHistoryPurgeTarget::id).toList());
    }

    /**
     * 아무도 가리키지 않게 된 이미지를 지운다.
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

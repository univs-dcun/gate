package ai.univs.gate.support.privacy;

import ai.univs.gate.support.privacy.DeletedFeatureImagePurgeService.Outcome;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 삭제한 특징점의 이미지 중 남은 것을 한 시간마다 파기한다 (UG-347). 즉시 파기({@link DeletedFeatureImagePurgeService#purgeQuietly})
 * 가 실패한 것과, 이 기능 이전에 쌓인 삭제분이 대상이다.
 *
 * <p><b>기본으로 켜져 있다.</b> {@link ProjectDataPurgeScheduler}·{@link HistoryPurgeScheduler} 가 기본 꺼짐인 이유는 보존
 * 기간이 법무·계약 결정이기 때문이다. 여기는 고객이 API 로 직접 삭제를 요청한 데이터라 기다릴 이유가 없다. 문제가 생기면
 * {@code gate.privacy.deleted-feature-image-purge.enabled=false} 로 즉시 파기와 함께 멈춘다.
 *
 * <p>실행당 상한을 두고 id 커서로 이어 간다 — 기존 삭제분이 많아도 다음 실행이 이어서 하고(시간당 500건, 하루 12,000건),
 * 계속 실패하는 건이 앞에 있어도 뒤의 대상이 막히지 않는다. 끝까지 가면(상한보다 적게 나오면) 처음부터 다시 돈다. 커서는
 * 메모리에만 둔다 — 재기동하면 처음부터지만, 처리한 행은 대상에서 빠지므로 다시 하는 일은 실패했던 건뿐이다.
 *
 * <p>실패는 건별 스택 대신 실행마다 요약 한 줄과 첫 원인 하나로 남긴다 — 볼륨·권한 문제 하나로 매시 수백 개의 스택이 쌓이지 않게.
 *
 * <p>gate 는 환경당 단일 인스턴스 전제다. 두 대가 같은 행을 집어도 파일 삭제는 「이미 없음」으로 끝나고 경로 비우기는 한쪽만
 * 1행을 얻어 결과는 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeletedFeatureImagePurgeScheduler {

    static final int MAX_PER_RUN = 500;

    private final DeletedFeatureImagePurgeService purgeService;

    /** 다음 실행이 이어 갈 위치 (마지막으로 본 id). */
    long cursor;

    /** 꺼져 있다는 안내는 기동 뒤 한 번만 — 매시 한 줄씩 쌓이지 않게. */
    private boolean disabledLogged;

    // 매시 15분. 새벽 정리 잡들(정각)과 겹치지 않게 비켜 둔다.
    @Scheduled(cron = "0 15 * * * *", zone = "UTC")
    public void purgeRemaining() {
        if (!purgeService.isEnabled()) {
            if (!disabledLogged) {
                log.info("삭제한 특징점의 이미지 파기는 꺼져 있다 (gate.privacy.deleted-feature-image-purge.enabled=false)");
                disabledLogged = true;
            }
            return;
        }
        List<Long> targets = purgeService.findTargets(cursor, MAX_PER_RUN);
        // 상한만큼 나왔으면 뒤에 더 있을 수 있다 — 거기서 잇는다. 아니면 끝까지 왔으니 다음은 처음부터.
        cursor = targets.size() == MAX_PER_RUN ? targets.get(targets.size() - 1) : 0L;
        if (targets.isEmpty()) {
            return;
        }
        Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
        int failed = 0;
        String firstFailure = null;
        for (Long featureSeq : targets) {
            try {
                counts.merge(purgeService.purge(featureSeq), 1, Integer::sum);
            } catch (RuntimeException e) {
                failed++;
                if (firstFailure == null) {
                    firstFailure = "featureSeq=" + featureSeq + ", 원인=" + e.getClass().getSimpleName() + ": " + e.getMessage();
                }
            }
        }
        log.info("삭제한 특징점의 이미지 정리. 대상={}, 결과={}, 실패={}", targets.size(), counts, failed);
        if (failed > 0) {
            log.warn("삭제한 특징점의 이미지 파기 실패 {}건 — 다음 실행에서 다시 시도한다. 첫 실패: {}", failed, firstFailure);
        }
        if (failed > 0 && failed == targets.size()) {
            log.error("이번 실행의 대상이 모두 실패했다 — 저장소 경로·권한·볼륨(file.root-path)을 확인할 것");
        }
    }
}

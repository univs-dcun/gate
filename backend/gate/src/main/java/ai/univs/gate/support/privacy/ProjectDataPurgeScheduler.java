package ai.univs.gate.support.privacy;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 삭제된 프로젝트의 생체 데이터를 유예 기간 뒤에 정리한다 (UG-303).
 *
 * <p><b>설정하지 않으면 아무것도 하지 않는다.</b>
 * {@code gate.privacy.project-purge.retention-days} 가 없거나 0 이하면 즉시 끝난다. 되돌릴 수
 * 없는 개인정보 삭제의 보존 기간은 제품·법무가 정할 값이지 코드가 기본값으로 정할 값이
 * 아니다 — e-KYC 이력이 규제 대상일 수 있고, 그 답이 나오기 전에 기본값으로 지우기 시작하면
 * 되돌릴 방법이 없다.
 *
 * <p>즉 이 클래스는 <b>결정이 내려졌을 때 한 줄로 켜지는 상태</b>로 들어간다. 켜기 전까지는
 * 기존과 완전히 같이 동작한다.
 *
 * <p>구조는 {@code auth-service} 의 리프레시 토큰 퍼지 잡(UG-250)을 따랐다 — 새벽 실행,
 * 실행당 상한, 건수 0 이어도 남기는 로그.
 *
 * <p>gate 는 환경당 단일 인스턴스 전제다. 다중 인스턴스로 가면 분산 락이 필요하다 — 두 대가
 * 같은 프로젝트를 동시에 정리하면 하위 서비스 삭제가 중복 호출된다(멱등하면 무해하지만
 * 확인된 바 없다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectDataPurgeScheduler {

    /** 실행당 처리할 프로젝트 수 상한. 누적분이 많은 첫 실행이 한 번에 끝나지 않게 한다. */
    private static final int MAX_PROJECTS_PER_RUN = 50;

    private final ProjectDataPurgeService purgeService;

    /**
     * 삭제 후 이 일수가 지난 프로젝트를 정리한다. <b>주지 않으면 기능이 꺼진다.</b>
     *
     * <p>설정 예 ({@code gate-config} 의 {@code gate-service.yml}):
     * <pre>{@code
     * gate:
     *   privacy:
     *     project-purge:
     *       retention-days: 30
     * }</pre>
     */
    @Value("${gate.privacy.project-purge.retention-days:0}")
    private int retentionDays;

    // 20:00 UTC = 05:00 KST. auth 의 퍼지 잡(19:00 UTC)과 한 시간 띄워 새벽 작업이 겹치지 않게 한다.
    @Scheduled(cron = "0 0 20 * * *", zone = "UTC")
    public void purgeDeletedProjectData() {
        if (retentionDays <= 0) {
            // 켜지지 않았다는 것을 하루 한 줄로 남긴다. "안 도는 것" 과 "꺼둔 것" 을 구분할
            // 유일한 근거다 — 나중에 켰다고 생각했는데 안 켜진 상황을 여기서 잡는다.
            //
            // INFO 인 이유: 루트 로그 레벨이 네 프로파일 모두 INFO 이고 ai.univs 오버라이드가
            // 없다. DEBUG 로 두면 이 줄이 어디에도 안 찍혀 위 목적을 달성하지 못한다
            // (반박 리뷰 지적).
            log.info("프로젝트 데이터 정리는 꺼져 있다 (gate.privacy.project-purge.retention-days 미설정)");
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(retentionDays);
        List<Long> targets = purgeService.findPurgeTargets(cutoff, MAX_PROJECTS_PER_RUN);

        int purgedFeatures = 0;
        for (Long projectId : targets) {
            try {
                purgedFeatures += purgeService.purgeProject(projectId);
            } catch (RuntimeException e) {
                // 한 프로젝트의 실패가 나머지를 막지 않는다. 그 프로젝트는 다음 실행에서 다시 온다.
                log.error("프로젝트 데이터 정리 실패 — 다음 실행에서 재시도한다. projectId={}", projectId, e);
            }
        }

        log.info("삭제된 프로젝트 데이터 정리 완료. 보존={}일, 기준={}, 프로젝트={}, 특징점={}",
                retentionDays, cutoff, targets.size(), purgedFeatures);

        if (targets.size() >= MAX_PROJECTS_PER_RUN) {
            log.warn("실행당 상한에 걸렸다 — 남은 프로젝트는 다음 실행에서 처리한다");
        }
    }
}

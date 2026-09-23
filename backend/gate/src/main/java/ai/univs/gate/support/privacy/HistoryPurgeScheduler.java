package ai.univs.gate.support.privacy;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.IntUnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 보존 기간이 지난 이력을 매일 정리한다 (UG-282).
 *
 * <p><b>설정하지 않으면 아무것도 하지 않는다.</b>
 * {@code gate.privacy.history-purge.retention-days} 가 없거나 0 이하면 즉시 끝난다. 이유는
 * {@link HistoryPurgeService} 의 설명 참고 — 이 이력은 위탁자 쪽 법령에서 보존 의무가 걸릴 수
 * 있어, 코드가 기본값을 정하면 고객사의 법정 의무를 우리가 깨는 형태가 된다.
 *
 * <p>gate 는 환경당 단일 인스턴스 전제다 (On-prem 확인, 2026-09-22). 다중 인스턴스로 가면
 * 두 대가 같은 배치를 집어 같은 파일을 두 번 지우려 한다 — 행 삭제는 두 번째가 0건이라
 * 무해하지만 파일 삭제는 그렇지 않을 수 있다. 그때는 분산 락이 필요하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryPurgeScheduler {

    /** 한 트랜잭션에서 지우는 행 수. */
    private static final int BATCH_SIZE = 500;

    /** 실행당 배치 수 상한. 밀린 분량이 많은 첫 실행이 새벽 내내 돌지 않게 한다. */
    private static final int MAX_BATCHES_PER_RUN = 20;

    /**
     * 금융 법령이 요구하는 최소 보존 기간 (5년).
     *
     * <p>근거는 <b>특정금융정보법 제5조의4</b> 다 — 고객확인자료를 금융거래등의 관계가
     * 종료한 때부터 5년 보존한다. 전자금융거래법(제22조①·시행령 제12조①)도 거래기록 보존을
     * 요구하지만 <b>기산점 문언이 없고 건당 1만원 이하는 1년</b>이라, 1825일의 근거로 삼지
     * 않는다(3차 반박 리뷰 지적 — 두 법의 구조가 다르다). 이보다 짧은 값을 넣는 것이
     * <b>틀렸다는 뜻은 아니다</b> — 출입문 게이트·B2B 납품처럼 금융 법령이 걸리지 않는 곳이 우리 타깃에 있고,
     * 그쪽은 오히려 짧아야 맞다 (개인정보 보호법 제21조: 목적 달성 시 지체 없이 파기).
     *
     * <p>그래서 막지 않고 경고만 한다. 기동을 막으면 정당한 비금융 납품이 뜨지 못한다.
     */
    private static final int 금융_법정_최소_보존일 = 1825;

    private final HistoryPurgeService purgeService;

    /**
     * 이 일수가 지난 이력을 지운다. <b>주지 않으면 기능이 꺼진다.</b>
     *
     * <p>설정 예 ({@code gate-config} 의 {@code gate-service.yml}):
     * <pre>{@code
     * gate:
     *   privacy:
     *     history-purge:
     *       retention-days: 1825
     * }</pre>
     */
    @Value("${gate.privacy.history-purge.retention-days:0}")
    private int retentionDays;

    // 21:00 UTC = 06:00 KST. auth 퍼지(19:00)·프로젝트 정리(20:00)와 한 시간씩 띄운다.
    @Scheduled(cron = "0 0 21 * * *", zone = "UTC")
    public void purgeExpiredHistory() {
        if (retentionDays <= 0) {
            // 꺼져 있다는 것을 하루 한 줄로 남긴다. "안 도는 것" 과 "꺼둔 것" 을 구분할 유일한
            // 근거다. INFO 인 이유는 루트 로그 레벨이 네 프로파일 모두 INFO 이기 때문이다.
            log.info("이력 정리는 꺼져 있다 (gate.privacy.history-purge.retention-days 미설정)");
            return;
        }

        if (retentionDays < 금융_법정_최소_보존일) {
            log.warn("""
                    이력 보존이 {}일로 금융 법정 최소(5년, {}일)보다 짧다. \
                    금융 납품이라면 특정금융정보법 제5조의4(고객확인자료, 거래 관계 종료 후 5년) 위반이 된다. \
                    비금융 납품이면 의도한 설정일 수 있다 — 계약을 확인할 것.""",
                    retentionDays, 금융_법정_최소_보존일);
        }

        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(retentionDays);

        int matches = drain("인증 이력", cutoff, b -> purgeService.purgeMatchHistoryBatch(cutoff, b));
        int features = drain("특징점 이력", cutoff, b -> purgeService.purgeFeatureHistoryBatch(cutoff, b));

        log.info("이력 정리 완료. 보존={}일, 기준={}, 인증={}행, 특징점={}행",
                retentionDays, cutoff, matches, features);
    }

    /**
     * 한 종류를 상한까지 비운다.
     *
     * <p>배치가 0 을 돌려주면 더 지울 것이 없다는 뜻이므로 멈춘다. 상한까지 갔으면 아직 남은
     * 것이고, 다음 실행이 이어 받는다.
     */
    private int drain(String 종류, LocalDateTime cutoff, IntUnaryOperator batch) {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            int deleted;
            try {
                deleted = batch.applyAsInt(BATCH_SIZE);
            } catch (RuntimeException e) {
                // 한 배치의 실패가 나머지를 막지 않는다 — 다른 종류는 계속 처리하고, 남은
                // 분량은 다음 실행에서 다시 온다.
                log.error("{} 정리 실패 — 다음 실행에서 재시도한다. 기준={}, 지금까지={}행",
                        종류, cutoff, total, e);
                return total;
            }
            if (deleted == 0) {
                return total;
            }
            total += deleted;
        }
        log.warn("{} 정리가 실행당 상한({}배치 × {}행)에 걸렸다 — 남은 분량은 다음 실행에서 처리한다",
                종류, MAX_BATCHES_PER_RUN, BATCH_SIZE);
        return total;
    }
}

package ai.univs.gate.support.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.facade.dashboard.application.result.DashboardDailyStatsResult;
import ai.univs.gate.facade.dashboard.application.result.DashboardRatiosResult;
import ai.univs.gate.facade.dashboard.application.result.DashboardTrendResult;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.support.jpa.JpaSliceTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 대시보드 인증 지표(1:1·1:1 사진·1:N·라이브니스)의 실제 쿼리.
 *
 * <p>UG-325 가 등록/삭제 집계를 고치면서 {@code DashboardStatsService} 가 뮤테이션 대상에 들어왔고, CI 가
 * 킬률 45% 를 보고했다. 생존 54개 중 대부분이 이 클래스의 <b>원래 테스트가 없던</b> 인증 지표 쪽이었다 —
 * 기간 필터·PALM 분기·추이 라벨·일일통계 페이징 전부. 여기서 그것을 덮는다.
 *
 * <p>시각은 "오늘(UTC)" 기준 상대값으로 준다. 라벨 생성이 {@code LocalDate.now(UTC)} 에 묶여 있어 절대
 * 날짜로는 테스트가 날짜에 따라 깨진다.
 */
@JpaSliceTest
@DisplayName("대시보드 인증 지표 집계 (match_history)")
class DashboardAuthMetricsSliceTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter M = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired private EntityManager em;
    private DashboardStatsService stats;
    private Project project;

    @BeforeEach
    void setUp() {
        stats = new DashboardStatsService(em);
        project = Project.builder().accountId(100L).projectName("t").branchName("b")
                .isDeleted(false).status(ProjectStatus.ACTIVE).build();
        em.persist(project);
    }

    private void 인증(MatchType type, FeatureType ft, boolean success, LocalDateTime at) {
        MatchHistory m = MatchHistory.builder().project(project).featureType(ft).matchType(type)
                .matchTime(at).checkLiveness(false).success(success)
                .transactionUuid(UUID.randomUUID().toString()).build();
        em.persist(m); em.flush();
        em.createQuery("UPDATE MatchHistory e SET e.createdAt = :at, e.updatedAt = :at WHERE e.id = :id")
                .setParameter("at", at).setParameter("id", m.getId()).executeUpdate();
        em.clear();
    }

    private static LocalDateTime 오늘(int hour) { return TODAY.atTime(hour, 0); }
    private static LocalDateTime 며칠전(int days) { return TODAY.minusDays(days).atTime(12, 0); }

    // ── 건수 ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("타입별 건수 — 기간 필터는 created_at, 레거시 VERIFY 는 1:1 촬영에 합산, 전체 누적은 기간 무관")
    void 타입별_건수() {
        인증(MatchType.VERIFY_ID,    FeatureType.FACE, true,  오늘(3));
        인증(MatchType.VERIFY,       FeatureType.FACE, false, 며칠전(3));    // 레거시 → verifyById
        인증(MatchType.VERIFY_IMAGE, FeatureType.FACE, true,  며칠전(3));
        인증(MatchType.IDENTIFY,     FeatureType.FACE, true,  며칠전(20));
        인증(MatchType.IDENTIFY,     FeatureType.FACE, false, 며칠전(400));  // 어느 기간에도 안 잡힘
        인증(MatchType.LIVENESS,     FeatureType.FACE, true,  며칠전(100));

        Long pid = project.getId();
        LocalDateTime week = DashboardStatsService.periodFrom(TrendPeriod.WEEK);
        LocalDateTime year = DashboardStatsService.periodFrom(TrendPeriod.YEAR);

        assertThat(stats.countVerifyById(pid, week, FeatureType.FACE)).isEqualTo(2);
        assertThat(stats.countVerifyByImage(pid, week, FeatureType.FACE)).isEqualTo(1);
        assertThat(stats.countIdentify(pid, week, FeatureType.FACE)).as("20일 전은 주간 밖").isEqualTo(0);
        assertThat(stats.countIdentify(pid, year, FeatureType.FACE)).as("400일 전은 연간 밖").isEqualTo(1);
        assertThat(stats.countLiveness(pid, week, FeatureType.FACE)).isEqualTo(0);
        assertThat(stats.countLiveness(pid, year, FeatureType.FACE)).isEqualTo(1);

        assertThat(stats.countTotalVerifyById(pid, FeatureType.FACE)).isEqualTo(2);
        assertThat(stats.countTotalVerifyByImage(pid, FeatureType.FACE)).isEqualTo(1);
        assertThat(stats.countTotalIdentify(pid, FeatureType.FACE)).as("전체 누적은 기간 무관").isEqualTo(2);
        assertThat(stats.countTotalLiveness(pid, FeatureType.FACE)).isEqualTo(1);
        assertThat(stats.countTotalIdentify(pid, FeatureType.PALM)).as("인증 방식이 다르면 0").isEqualTo(0);
    }

    // ── 비율 ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("비율은 (성공, 실패) — PALM 은 1:1 두 항목이 항상 (0,0)")
    void 비율() {
        인증(MatchType.VERIFY_ID,    FeatureType.FACE, true,  오늘(1));
        인증(MatchType.VERIFY,       FeatureType.FACE, false, 오늘(2));
        인증(MatchType.VERIFY_IMAGE, FeatureType.FACE, false, 오늘(3));
        인증(MatchType.IDENTIFY,     FeatureType.FACE, true,  오늘(4));
        인증(MatchType.IDENTIFY,     FeatureType.FACE, true,  오늘(5));
        인증(MatchType.IDENTIFY,     FeatureType.FACE, false, 오늘(6));
        인증(MatchType.LIVENESS,     FeatureType.FACE, true,  오늘(7));
        인증(MatchType.IDENTIFY,     FeatureType.PALM, true,  오늘(8));
        인증(MatchType.VERIFY_IMAGE, FeatureType.PALM, true,  오늘(9));   // 있어도 PALM 비율에는 안 잡혀야 한다

        LocalDateTime from = DashboardStatsService.periodFrom(TrendPeriod.WEEK);
        DashboardRatiosResult face = stats.getRatios(project.getId(), from, FeatureType.FACE);
        assertThat(face.verifyById()).isEqualTo(new DashboardRatiosResult.RatioItem(1, 1));
        assertThat(face.verifyByImage()).isEqualTo(new DashboardRatiosResult.RatioItem(0, 1));
        assertThat(face.identify()).isEqualTo(new DashboardRatiosResult.RatioItem(2, 1));
        assertThat(face.liveness()).isEqualTo(new DashboardRatiosResult.RatioItem(1, 0));

        DashboardRatiosResult palm = stats.getRatios(project.getId(), from, FeatureType.PALM);
        assertThat(palm.verifyById()).as("PALM 1:1 촬영은 없는 기능").isEqualTo(new DashboardRatiosResult.RatioItem(0, 0));
        assertThat(palm.verifyByImage()).as("PALM 1:1 사진도 없는 기능").isEqualTo(new DashboardRatiosResult.RatioItem(0, 0));
        assertThat(palm.identify()).isEqualTo(new DashboardRatiosResult.RatioItem(1, 0));
    }

    // ── 추이 ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("추이 라벨 — TODAY 24시간, WEEK 7일, MONTH 30일, YEAR 12개월, 마지막 라벨은 오늘")
    void 추이_라벨() {
        DashboardTrendResult today = stats.getTrend(project.getId(), TrendPeriod.TODAY, FeatureType.FACE);
        assertThat(today.labels()).hasSize(24).startsWith("00").endsWith("23");

        DashboardTrendResult week = stats.getTrend(project.getId(), TrendPeriod.WEEK, FeatureType.FACE);
        assertThat(week.labels()).hasSize(7).startsWith(TODAY.minusDays(6).format(D)).endsWith(TODAY.format(D));

        DashboardTrendResult month = stats.getTrend(project.getId(), TrendPeriod.MONTH, FeatureType.FACE);
        assertThat(month.labels()).hasSize(30).startsWith(TODAY.minusDays(29).format(D)).endsWith(TODAY.format(D));

        DashboardTrendResult year = stats.getTrend(project.getId(), TrendPeriod.YEAR, FeatureType.FACE);
        assertThat(year.labels()).hasSize(12).startsWith(TODAY.minusMonths(11).withDayOfMonth(1).format(M)).endsWith(TODAY.format(M));
        assertThat(year.period()).isEqualTo(TrendPeriod.YEAR);
    }

    @Test
    @DisplayName("추이 값 — 라벨 자리에 그날(그 시각)의 건수가 놓이고, PALM 은 1:1 두 계열이 전부 0")
    void 추이_값() {
        인증(MatchType.VERIFY_ID,    FeatureType.FACE, true,  오늘(3));
        인증(MatchType.VERIFY,       FeatureType.FACE, true,  오늘(3));
        인증(MatchType.VERIFY_IMAGE, FeatureType.FACE, true,  오늘(3));
        인증(MatchType.IDENTIFY,     FeatureType.FACE, true,  며칠전(2));
        인증(MatchType.LIVENESS,     FeatureType.FACE, false, 며칠전(2));
        인증(MatchType.VERIFY_IMAGE, FeatureType.PALM, true,  오늘(3));
        인증(MatchType.IDENTIFY,     FeatureType.PALM, true,  오늘(3));

        DashboardTrendResult week = stats.getTrend(project.getId(), TrendPeriod.WEEK, FeatureType.FACE);
        int 오늘i = week.labels().indexOf(TODAY.format(D));
        int 이틀전i = week.labels().indexOf(TODAY.minusDays(2).format(D));
        assertThat(week.verifyById().get(오늘i)).as("VERIFY_ID + 레거시 VERIFY").isEqualTo(2L);
        assertThat(week.verifyByImage().get(오늘i)).isEqualTo(1L);
        assertThat(week.identify().get(이틀전i)).isEqualTo(1L);
        assertThat(week.liveness().get(이틀전i)).as("실패도 시도 건수에 든다").isEqualTo(1L);
        assertThat(week.identify().get(오늘i)).as("PALM 1:N 은 FACE 추이에 안 섞인다").isEqualTo(0L);
        assertThat(week.registration()).as("feature_history 가 비어 있으니 등록은 전부 0").containsOnly(0L);

        DashboardTrendResult todayTrend = stats.getTrend(project.getId(), TrendPeriod.TODAY, FeatureType.FACE);
        assertThat(todayTrend.verifyByImage().get(todayTrend.labels().indexOf("03"))).isEqualTo(1L);

        DashboardTrendResult palm = stats.getTrend(project.getId(), TrendPeriod.WEEK, FeatureType.PALM);
        assertThat(palm.verifyById()).containsOnly(0L);
        assertThat(palm.verifyByImage()).as("PALM 은 VERIFY_IMAGE 행이 있어도 계열이 비어야 한다").containsOnly(0L);
        assertThat(palm.identify().get(오늘i)).isEqualTo(1L);

        DashboardTrendResult year = stats.getTrend(project.getId(), TrendPeriod.YEAR, FeatureType.FACE);
        assertThat(year.verifyById().get(year.labels().indexOf(TODAY.format(M)))).isEqualTo(2L);
    }

    // ── 일일 통계 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("일일 통계 — 날짜 최신순, 페이지 크기로 잘라 총 페이지를 올림 계산, PALM 은 1:1 두 열이 0")
    void 일일_통계() {
        인증(MatchType.VERIFY_ID,    FeatureType.FACE, true, 오늘(1));
        인증(MatchType.IDENTIFY,     FeatureType.FACE, true, 오늘(2));
        인증(MatchType.VERIFY_IMAGE, FeatureType.FACE, true, 며칠전(1));
        인증(MatchType.LIVENESS,     FeatureType.FACE, true, 며칠전(5));
        인증(MatchType.VERIFY_IMAGE, FeatureType.PALM, true, 며칠전(5));
        인증(MatchType.IDENTIFY,     FeatureType.PALM, true, 며칠전(5));

        DateTimeFormatter slash = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        DashboardDailyStatsResult p1 = stats.getDailyStats(project.getId(), 1, 2, FeatureType.FACE);
        assertThat(p1.items()).extracting(i -> i.date())
                .containsExactly(TODAY.format(slash), TODAY.minusDays(1).format(slash));
        assertThat(p1.items().get(0).verifyById()).isEqualTo(1L);
        assertThat(p1.items().get(0).identify()).isEqualTo(1L);
        assertThat(p1.items().get(1).verifyByImage()).isEqualTo(1L);
        assertThat(p1.page().totalElements()).as("FACE 날짜 3개").isEqualTo(3L);
        assertThat(p1.page().totalPages()).as("3개를 2개씩 → 2페이지 (올림)").isEqualTo(2);

        DashboardDailyStatsResult p2 = stats.getDailyStats(project.getId(), 2, 2, FeatureType.FACE);
        assertThat(p2.items()).extracting(i -> i.date()).containsExactly(TODAY.minusDays(5).format(slash));
        assertThat(p2.items().get(0).liveness()).isEqualTo(1L);

        DashboardDailyStatsResult palm = stats.getDailyStats(project.getId(), 1, 10, FeatureType.PALM);
        assertThat(palm.items()).hasSize(1);
        assertThat(palm.items().get(0).verifyByImage()).as("PALM 1:1 사진 열은 항상 0").isEqualTo(0L);
        assertThat(palm.items().get(0).verifyById()).isEqualTo(0L);
        assertThat(palm.items().get(0).identify()).isEqualTo(1L);

        DashboardDailyStatsResult empty = stats.getDailyStats(project.getId() + 999, 1, 10, FeatureType.FACE);
        assertThat(empty.items()).isEmpty();
        assertThat(empty.page().totalPages()).as("데이터가 없어도 1페이지").isEqualTo(1);
    }
}

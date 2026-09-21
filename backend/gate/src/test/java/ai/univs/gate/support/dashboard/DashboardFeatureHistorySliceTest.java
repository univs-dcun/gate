package ai.univs.gate.support.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.facade.dashboard.application.result.DashboardRatiosResult;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.FeatureHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.persistence.FeatureHistoryJpaRepository;
import ai.univs.gate.modules.feature.infrastructure.persistence.FeatureHistoryRepositoryImpl;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.support.jpa.JpaSliceTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import ai.univs.gate.facade.dashboard.application.result.DashboardTrendResult;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * UG-325: 대시보드 등록/삭제 집계가 <b>이력</b>을 세는지.
 *
 * <p>예전 구현은 {@code biometric_feature} 를 {@code is_deleted=false} 로 세면서 그것을 "등록" 이라
 * 불렀다. 그래서 세 가지가 틀렸다 — 이 테스트의 세 본문이 각각 그것을 못박는다.
 * <ol>
 *   <li>삭제하면 <b>과거 달의 등록 수가 소급해서 줄었다.</b></li>
 *   <li>"전체 총 누적" 이 누적이 아니라 <b>현재 잔존 수</b>였다.</li>
 *   <li>등록/삭제 비율의 삭제 건수가 삭제 시각이 아니라 <b>등록 시각</b>으로 걸러졌다 — 7월에
 *       등록하고 9월에 지운 건은 9월 삭제에 안 잡혔다. 삭제 시각을 아무도 기록하지 않았으니
 *       셀 방법이 없었다.</li>
 * </ol>
 *
 * <p>{@code DashboardStatsService} 는 {@code EntityManager} 만 받는 {@code @Service} 라 슬라이스에서
 * 그대로 생성할 수 있다. QueryDSL 은 실제 SQL 을 내보내야 검증이 되므로 Mockito 로는 이 테스트를
 * 대체할 수 없다.
 *
 * <p>사건 시각은 저장 뒤 JPQL 로 덮어쓴다. {@code @CreatedDate} 는 저장 시점에 감사 핸들러가 채우기
 * 때문에 빌더로는 과거 시각을 줄 수 없다.
 */
@JpaSliceTest
@DisplayName("UG-325: 대시보드 등록/삭제 집계는 feature_history 를 센다")
class DashboardFeatureHistorySliceTest {

    private static final LocalDateTime 팔월_1일 = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final LocalDateTime 구월_1일 = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime 팔월_10일 = LocalDateTime.of(2026, 8, 10, 12, 0);
    private static final LocalDateTime 구월_5일 = LocalDateTime.of(2026, 9, 5, 12, 0);

    @Autowired
    private EntityManager em;
    @Autowired
    private FeatureHistoryJpaRepository featureHistoryJpaRepository;
    private FeatureHistoryRepository featureHistories;

    private DashboardStatsService stats;
    private Project project;

    @BeforeEach
    void setUp() {
        stats = new DashboardStatsService(em);
        featureHistories = new FeatureHistoryRepositoryImpl(featureHistoryJpaRepository);
        project = Project.builder()
                .accountId(100L)
                .projectName("테스트")
                .branchName("branch-1")
                .isDeleted(false)
                .status(ProjectStatus.ACTIVE)
                .build();
        em.persist(project);
    }

    // ── 픽스처 ─────────────────────────────────────────────────────────────────────

    private BiometricFeature 특징점(FeatureType type, String featureId) {
        BiometricFeature f = BiometricFeature.builder()
                .project(project)
                .type(type)
                .featureId(featureId)
                .description("메모-" + featureId)
                .isDeleted(false)
                .transactionUuid(UUID.randomUUID().toString())
                .build();
        em.persist(f);
        return f;
    }

    private FeatureHistory 등록(FeatureType type, String featureId, LocalDateTime at, boolean success) {
        FeatureHistory h = FeatureHistory.register(project, type, false, null, UUID.randomUUID().toString(), true);
        if (success) {
            h.successRegister(특징점(type, featureId));
        } else {
            h.fail("FAKE");
        }
        h = featureHistories.save(h);
        시각을_덮어쓴다(h, at);
        return h;
    }

    private FeatureHistory 삭제(BiometricFeature target, LocalDateTime at, boolean success) {
        FeatureHistory h = FeatureHistory.delete(project, target, UUID.randomUUID().toString());
        if (success) {
            h.successDelete();
            target.delete();
        } else {
            h.fail("INTERNAL_SERVER_ERROR");
        }
        h = featureHistories.save(h);
        시각을_덮어쓴다(h, at);
        return h;
    }

    private void 시각을_덮어쓴다(FeatureHistory h, LocalDateTime at) {
        em.flush();
        em.createQuery("UPDATE FeatureHistory f SET f.createdAt = :at, f.updatedAt = :at WHERE f.id = :id")
                .setParameter("at", at)
                .setParameter("id", h.getId())
                .executeUpdate();
        em.clear();
    }

    // ── 본문 ───────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("① 삭제해도 과거 기간의 등록 수는 줄지 않는다")
    void 등록_수는_소급되지_않는다() {
        등록(FeatureType.FACE, "f-1", 팔월_10일, true);
        등록(FeatureType.FACE, "f-2", 팔월_10일, true);
        FeatureHistory 셋째 = 등록(FeatureType.FACE, "f-3", 팔월_10일, true);

        BiometricFeature 지울_것 = em.find(BiometricFeature.class, 셋째.getFeatureSeq());
        삭제(지울_것, 구월_5일, true);

        // 옛 구현(biometric_feature is_deleted=false 카운트)이었다면 2 가 나온다.
        assertThat(stats.countRegistrations(project.getId(), 팔월_1일, FeatureType.FACE))
                .as("8월 등록 3건은 9월에 하나를 지워도 3건이어야 한다")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("② '전체 총 누적' 은 지워진 특징점의 등록도 포함한 진짜 누적이다")
    void 전체_누적은_잔존_수가_아니다() {
        FeatureHistory a = 등록(FeatureType.FACE, "f-1", 팔월_10일, true);
        FeatureHistory b = 등록(FeatureType.FACE, "f-2", 팔월_10일, true);
        삭제(em.find(BiometricFeature.class, a.getFeatureSeq()), 구월_5일, true);
        삭제(em.find(BiometricFeature.class, b.getFeatureSeq()), 구월_5일, true);

        assertThat(stats.countTotalRegistrations(project.getId(), FeatureType.FACE))
                .as("둘 다 지웠어도 등록은 2건 있었다 — 잔존 수(0)가 아니다")
                .isEqualTo(2);
        assertThat(stats.countTotalDeletions(project.getId(), FeatureType.FACE)).isEqualTo(2);
    }

    @Test
    @DisplayName("③ 등록/삭제 비율의 삭제 건수는 삭제 시각 기준이다")
    void 삭제는_삭제_시각으로_센다() {
        FeatureHistory 팔월_등록 = 등록(FeatureType.FACE, "f-1", 팔월_10일, true);
        삭제(em.find(BiometricFeature.class, 팔월_등록.getFeatureSeq()), 구월_5일, true);

        // 9월 기간: 등록 0, 삭제 1.
        // 옛 구현은 total(created>=9월) - active(created>=9월) = 0 - 0 = 0 으로 이 삭제를 놓쳤다.
        DashboardRatiosResult 구월 = stats.getRatios(project.getId(), 구월_1일, FeatureType.FACE);
        assertThat(구월.registration().primaryCount()).as("9월 등록").isEqualTo(0);
        assertThat(구월.registration().secondaryCount()).as("9월 삭제 — 8월 등록건이라도 9월에 지웠으면 잡혀야 한다").isEqualTo(1);

        // 8월 기간: 등록 1, 삭제 0 — 삭제는 8월 사건이 아니다.
        DashboardRatiosResult 팔월 = stats.getRatios(project.getId(), 팔월_1일, FeatureType.FACE);
        assertThat(팔월.registration().primaryCount()).isEqualTo(1);
        assertThat(stats.countDeletions(project.getId(), 팔월_1일, FeatureType.FACE))
                .as("8월 1일 이후 전체로 보면 9월 삭제가 포함된다").isEqualTo(1);
    }

    @Test
    @DisplayName("실패한 등록·삭제 시도는 이력에는 남되 지표에는 잡히지 않는다")
    void 실패는_세지_않는다() {
        등록(FeatureType.FACE, "f-ok", 팔월_10일, true);
        등록(FeatureType.FACE, "f-fail", 팔월_10일, false);
        BiometricFeature 살아있는_것 = em.find(BiometricFeature.class,
                em.createQuery("SELECT b.id FROM BiometricFeature b WHERE b.featureId = 'f-ok'", Long.class).getSingleResult());
        삭제(살아있는_것, 구월_5일, false);

        assertThat(stats.countTotalRegistrations(project.getId(), FeatureType.FACE)).isEqualTo(1);
        assertThat(stats.countTotalDeletions(project.getId(), FeatureType.FACE)).isEqualTo(0);
        assertThat(em.createQuery("SELECT COUNT(f) FROM FeatureHistory f", Long.class).getSingleResult())
                .as("행 자체는 3개 전부 남아 있다").isEqualTo(3L);
    }

    @Test
    @DisplayName("인증 방식(FACE/PALM)은 서로 섞이지 않는다")
    void 인증_방식이_분리된다() {
        등록(FeatureType.FACE, "f-1", 팔월_10일, true);
        등록(FeatureType.PALM, "p-1", 팔월_10일, true);
        등록(FeatureType.PALM, "p-2", 팔월_10일, true);

        assertThat(stats.countTotalRegistrations(project.getId(), FeatureType.FACE)).isEqualTo(1);
        assertThat(stats.countTotalRegistrations(project.getId(), FeatureType.PALM)).isEqualTo(2);
    }

    @Test
    @DisplayName("등록 추이 — 시간(TODAY)·일(WEEK)·월(YEAR) 라벨 자리에 등록 건수가 놓이고, 지워도 줄지 않는다")
    void 등록_추이() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        FeatureHistory a = 등록(FeatureType.FACE, "t-1", today.atTime(3, 0), true);
        등록(FeatureType.FACE, "t-2", today.atTime(3, 30), true);
        등록(FeatureType.FACE, "t-3", today.minusDays(2).atTime(9, 0), true);
        등록(FeatureType.PALM, "t-p", today.atTime(3, 0), true);
        삭제(em.find(BiometricFeature.class, a.getFeatureSeq()), today.atTime(4, 0), true);

        DashboardTrendResult day = stats.getTrend(project.getId(), TrendPeriod.TODAY, FeatureType.FACE);
        assertThat(day.registration().get(day.labels().indexOf("03"))).as("03시 등록 2건 — 하나를 지웠어도").isEqualTo(2L);
        assertThat(day.registration().get(day.labels().indexOf("04"))).as("삭제는 등록 계열에 안 든다").isEqualTo(0L);

        DashboardTrendResult week = stats.getTrend(project.getId(), TrendPeriod.WEEK, FeatureType.FACE);
        DateTimeFormatter d = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        assertThat(week.registration().get(week.labels().indexOf(today.format(d)))).isEqualTo(2L);
        assertThat(week.registration().get(week.labels().indexOf(today.minusDays(2).format(d)))).isEqualTo(1L);
        assertThat(week.registration().stream().mapToLong(Long::longValue).sum()).as("PALM 은 FACE 추이에 안 섞인다").isEqualTo(3L);

        DashboardTrendResult year = stats.getTrend(project.getId(), TrendPeriod.YEAR, FeatureType.FACE);
        long thisMonth = year.registration().get(year.labels().indexOf(today.format(DateTimeFormatter.ofPattern("yyyy-MM"))));
        assertThat(thisMonth).isGreaterThanOrEqualTo(2L);
    }

    @Test
    @DisplayName("추이·일일통계도 이력 기준으로 나온다 (TO_CHAR 경로)")
    void 추이와_일일통계() {
        등록(FeatureType.FACE, "f-1", 팔월_10일, true);
        FeatureHistory b = 등록(FeatureType.FACE, "f-2", 팔월_10일, true);
        삭제(em.find(BiometricFeature.class, b.getFeatureSeq()), 구월_5일, true);

        var daily = stats.getDailyStats(project.getId(), 1, 10, FeatureType.FACE);
        assertThat(daily.items()).anySatisfy(item -> {
            assertThat(item.date()).isEqualTo("2026/08/10");
            assertThat(item.registration()).as("지웠어도 8월 10일 등록은 2건").isEqualTo(2);
        });
    }
}

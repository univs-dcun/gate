package ai.univs.gate.support.dashboard;

import ai.univs.gate.facade.dashboard.application.result.DashboardDailyStatItemResult;
import ai.univs.gate.facade.dashboard.application.result.DashboardDailyStatsResult;
import ai.univs.gate.facade.dashboard.application.result.DashboardRatiosResult;
import ai.univs.gate.facade.dashboard.application.result.DashboardTrendResult;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import ai.univs.gate.modules.feature.domain.entity.QFeatureHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureActionType;
import ai.univs.gate.modules.feature.domain.entity.QMatchHistory;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.shared.usecase.result.CustomPageResult;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringTemplate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class DashboardStatsService {

    private final JPAQueryFactory queryFactory;

    private final QMatchHistory mh = QMatchHistory.matchHistory;
    // UG-325: 등록·삭제는 feature_history(append-only)에서 센다. 예전에는 biometric_feature 를
    // is_deleted=false 로 세어 "등록" 이라 불렀는데, 그건 현재 잔존 수다 — 삭제하면 과거 달의
    // 등록 수가 소급해서 줄고, "전체 총 누적" 은 누적이 아니었고, 등록/삭제 비율의 삭제 건수는
    // 삭제 시각이 아니라 등록 시각으로 걸러졌다 (7월 등록·9월 삭제가 9월에 안 잡힘).
    private final QFeatureHistory fh = QFeatureHistory.featureHistory;

    public DashboardStatsService(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    // ── 단순 건수 집계 (기간 필터) ─────────────────────────────────────────────────

    public long countRegistrations(Long projectId, LocalDateTime from, FeatureType featureType) {
        return countFeatureEvents(projectId, from, featureType, FeatureActionType.REGISTER);
    }

    public long countDeletions(Long projectId, LocalDateTime from, FeatureType featureType) {
        return countFeatureEvents(projectId, from, featureType, FeatureActionType.DELETE);
    }

    public long countVerifyById(Long projectId, LocalDateTime from, FeatureType featureType) {
        Long count = queryFactory.select(mh.count()).from(mh)
                .where(mh.project.id.eq(projectId), mh.featureType.eq(featureType), mh.matchType.in(MatchType.VERIFY_ID, MatchType.VERIFY), mh.createdAt.goe(from))
                .fetchOne();
        return Optional.ofNullable(count).orElse(0L);
    }

    public long countVerifyByImage(Long projectId, LocalDateTime from, FeatureType featureType) {
        Long count = queryFactory.select(mh.count()).from(mh)
                .where(mh.project.id.eq(projectId), mh.featureType.eq(featureType), mh.matchType.eq(MatchType.VERIFY_IMAGE), mh.createdAt.goe(from))
                .fetchOne();
        return Optional.ofNullable(count).orElse(0L);
    }

    public long countIdentify(Long projectId, LocalDateTime from, FeatureType featureType) {
        Long count = queryFactory.select(mh.count()).from(mh)
                .where(mh.project.id.eq(projectId), mh.featureType.eq(featureType), mh.matchType.eq(MatchType.IDENTIFY), mh.createdAt.goe(from))
                .fetchOne();
        return Optional.ofNullable(count).orElse(0L);
    }

    public long countLiveness(Long projectId, LocalDateTime from, FeatureType featureType) {
        Long count = queryFactory.select(mh.count()).from(mh)
                .where(mh.project.id.eq(projectId), mh.featureType.eq(featureType), mh.matchType.eq(MatchType.LIVENESS), mh.createdAt.goe(from))
                .fetchOne();
        return Optional.ofNullable(count).orElse(0L);
    }

    // ── 단순 건수 집계 (전체 누적) ─────────────────────────────────────────────────

    /** 진짜 누적이다 — 지워진 특징점의 등록도 셈에 남는다. */
    public long countTotalRegistrations(Long projectId, FeatureType featureType) {
        return countFeatureEvents(projectId, null, featureType, FeatureActionType.REGISTER);
    }

    public long countTotalDeletions(Long projectId, FeatureType featureType) {
        return countFeatureEvents(projectId, null, featureType, FeatureActionType.DELETE);
    }

    public long countTotalVerifyById(Long projectId, FeatureType featureType) {
        Long count = queryFactory.select(mh.count()).from(mh)
                .where(mh.project.id.eq(projectId), mh.featureType.eq(featureType), mh.matchType.in(MatchType.VERIFY_ID, MatchType.VERIFY))
                .fetchOne();
        return Optional.ofNullable(count).orElse(0L);
    }

    public long countTotalVerifyByImage(Long projectId, FeatureType featureType) {
        Long count = queryFactory.select(mh.count()).from(mh)
                .where(mh.project.id.eq(projectId), mh.featureType.eq(featureType), mh.matchType.eq(MatchType.VERIFY_IMAGE))
                .fetchOne();
        return Optional.ofNullable(count).orElse(0L);
    }

    public long countTotalIdentify(Long projectId, FeatureType featureType) {
        Long count = queryFactory.select(mh.count()).from(mh)
                .where(mh.project.id.eq(projectId), mh.featureType.eq(featureType), mh.matchType.eq(MatchType.IDENTIFY))
                .fetchOne();
        return Optional.ofNullable(count).orElse(0L);
    }

    public long countTotalLiveness(Long projectId, FeatureType featureType) {
        Long count = queryFactory.select(mh.count()).from(mh)
                .where(mh.project.id.eq(projectId), mh.featureType.eq(featureType), mh.matchType.eq(MatchType.LIVENESS))
                .fetchOne();
        return Optional.ofNullable(count).orElse(0L);
    }

    // ── 비율 통계 ────────────────────────────────────────────────────────────────────

    public DashboardRatiosResult getRatios(Long projectId, LocalDateTime from, FeatureType featureType) {
        return new DashboardRatiosResult(
                queryRegistrationRatio(projectId, from, featureType),
                featureType == FeatureType.PALM ? new DashboardRatiosResult.RatioItem(0, 0) : queryVerifyByIdRatio(projectId, from),
                featureType == FeatureType.PALM ? new DashboardRatiosResult.RatioItem(0, 0) : queryMatchRatio(projectId, from, featureType, MatchType.VERIFY_IMAGE),
                queryMatchRatio(projectId, from, featureType, MatchType.IDENTIFY),
                queryMatchRatio(projectId, from, featureType, MatchType.LIVENESS)
        );
    }

    // ── 사용량 추이 ─────────────────────────────────────────────────────────────────

    public DashboardTrendResult getTrend(Long projectId, TrendPeriod period, FeatureType featureType) {
        boolean byHour  = period == TrendPeriod.TODAY;
        boolean byMonth = period == TrendPeriod.YEAR;

        LocalDateTime from = switch (period) {
            case TODAY -> LocalDate.now(ZoneOffset.UTC).atStartOfDay();
            case WEEK  -> LocalDate.now(ZoneOffset.UTC).minusDays(6).atStartOfDay();
            case MONTH -> LocalDate.now(ZoneOffset.UTC).minusDays(29).atStartOfDay();
            case YEAR  -> LocalDate.now(ZoneOffset.UTC).minusMonths(11).withDayOfMonth(1).atStartOfDay();
        };

        List<String> labels = generateLabels(period);

        Map<String, Long> regMap      = queryRegistrationByDate(projectId, from, byMonth, byHour, featureType);
        Map<String, Long> verByIdMap  = featureType == FeatureType.PALM ? Map.of() : queryVerifyByIdByDate(projectId, from, byMonth, byHour);
        Map<String, Long> verByImgMap = featureType == FeatureType.PALM ? Map.of() : queryMatchByDate(projectId, from, byMonth, byHour, featureType, MatchType.VERIFY_IMAGE);
        Map<String, Long> idnMap      = queryMatchByDate(projectId, from, byMonth, byHour, featureType, MatchType.IDENTIFY);
        Map<String, Long> livMap      = queryMatchByDate(projectId, from, byMonth, byHour, featureType, MatchType.LIVENESS);

        return new DashboardTrendResult(
                period,
                labels,
                labels.stream().map(l -> regMap.getOrDefault(l, 0L)).toList(),
                labels.stream().map(l -> verByIdMap.getOrDefault(l, 0L)).toList(),
                labels.stream().map(l -> verByImgMap.getOrDefault(l, 0L)).toList(),
                labels.stream().map(l -> idnMap.getOrDefault(l, 0L)).toList(),
                labels.stream().map(l -> livMap.getOrDefault(l, 0L)).toList()
        );
    }

    // ── 일일 데이터 통계 ────────────────────────────────────────────────────────────

    public DashboardDailyStatsResult getDailyStats(Long projectId, int page, int pageSize, FeatureType featureType) {
        Map<LocalDate, Long> regMap      = queryAllRegistrationByDate(projectId, featureType);
        Map<LocalDate, Long> verByIdMap  = featureType == FeatureType.PALM ? Map.of() : queryAllVerifyByIdByDate(projectId);
        Map<LocalDate, Long> verByImgMap = featureType == FeatureType.PALM ? Map.of() : queryAllMatchByDate(projectId, featureType, MatchType.VERIFY_IMAGE);
        Map<LocalDate, Long> idnMap      = queryAllMatchByDate(projectId, featureType, MatchType.IDENTIFY);
        Map<LocalDate, Long> livMap      = queryAllMatchByDate(projectId, featureType, MatchType.LIVENESS);

        // 전체 날짜 합집합 — 최신순 정렬
        Set<LocalDate> allDates = new TreeSet<>(Comparator.reverseOrder());
        allDates.addAll(regMap.keySet());
        allDates.addAll(verByIdMap.keySet());
        allDates.addAll(verByImgMap.keySet());
        allDates.addAll(idnMap.keySet());
        allDates.addAll(livMap.keySet());

        long totalElements = allDates.size();
        int  totalPages    = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / pageSize);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd");

        List<DashboardDailyStatItemResult> items = allDates.stream()
                .skip((long) (page - 1) * pageSize)
                .limit(pageSize)
                .map(d -> new DashboardDailyStatItemResult(
                        d.format(fmt),
                        regMap.getOrDefault(d, 0L),
                        verByIdMap.getOrDefault(d, 0L),
                        verByImgMap.getOrDefault(d, 0L),
                        idnMap.getOrDefault(d, 0L),
                        livMap.getOrDefault(d, 0L)
                ))
                .toList();

        CustomPageResult pageResult = new CustomPageResult(pageSize, page, totalElements, totalPages, 0L);
        return new DashboardDailyStatsResult(items, pageResult);
    }

    // ── private 헬퍼: 기간 시작일 계산 ──────────────────────────────────────────────

    public static LocalDateTime periodFrom(TrendPeriod period) {
        return switch (period) {
            case TODAY -> LocalDate.now(ZoneOffset.UTC).atStartOfDay();
            case WEEK  -> LocalDate.now(ZoneOffset.UTC).minusDays(6).atStartOfDay();
            case MONTH -> LocalDate.now(ZoneOffset.UTC).minusDays(29).atStartOfDay();
            case YEAR  -> LocalDate.now(ZoneOffset.UTC).minusMonths(11).withDayOfMonth(1).atStartOfDay();
        };
    }

    // ── private 헬퍼: 비율 집계 ─────────────────────────────────────────────────────

    /**
     * 등록/삭제 비율. 양쪽 모두 <b>사건이 일어난 시각</b>으로 기간을 거른다.
     *
     * <p>예전 구현은 {@code total(created_at>=from) - active(created_at>=from)} 이었다 — 즉
     * "이 기간에 등록된 것 중 지금 지워진 것" 이지 "이 기간에 지워진 것" 이 아니었다. 삭제 시각을
     * 아무도 기록하지 않아 셀 방법이 없었던 것이고, feature_history 가 그 시각을 준다.
     */
    private DashboardRatiosResult.RatioItem queryRegistrationRatio(Long projectId, LocalDateTime from, FeatureType featureType) {
        return new DashboardRatiosResult.RatioItem(
                countFeatureEvents(projectId, from, featureType, FeatureActionType.REGISTER),
                countFeatureEvents(projectId, from, featureType, FeatureActionType.DELETE));
    }

    /** 성공한 사건만 센다 — 실패한 등록·삭제 시도는 목록에는 남되 지표에는 잡히지 않는다. */
    private long countFeatureEvents(Long projectId, LocalDateTime from, FeatureType featureType, FeatureActionType action) {
        Long count = queryFactory.select(fh.count()).from(fh)
                .where(featureEvents(projectId, from, featureType, action))
                .fetchOne();
        return Optional.ofNullable(count).orElse(0L);
    }

    private BooleanBuilder featureEvents(Long projectId, LocalDateTime from, FeatureType featureType, FeatureActionType action) {
        BooleanBuilder where = new BooleanBuilder()
                .and(fh.project.id.eq(projectId))
                .and(fh.featureType.eq(featureType))
                .and(fh.actionType.eq(action))
                .and(fh.success.isTrue());
        if (from != null) {
            where.and(fh.createdAt.goe(from));
        }
        return where;
    }

    private DashboardRatiosResult.RatioItem queryVerifyByIdRatio(Long projectId, LocalDateTime from) {
        Long success = queryFactory.select(mh.count()).from(mh)
                .where(mh.project.id.eq(projectId), mh.matchType.in(MatchType.VERIFY_ID, MatchType.VERIFY), mh.success.eq(true), mh.createdAt.goe(from))
                .fetchOne();
        Long total = queryFactory.select(mh.count()).from(mh)
                .where(mh.project.id.eq(projectId), mh.matchType.in(MatchType.VERIFY_ID, MatchType.VERIFY), mh.createdAt.goe(from))
                .fetchOne();
        long successCount = Optional.ofNullable(success).orElse(0L);
        long totalCount   = Optional.ofNullable(total).orElse(0L);
        return new DashboardRatiosResult.RatioItem(successCount, totalCount - successCount);
    }

    private DashboardRatiosResult.RatioItem queryMatchRatio(Long projectId, LocalDateTime from, FeatureType fType, MatchType mType) {
        Long success = queryFactory.select(mh.count()).from(mh)
                .where(mh.project.id.eq(projectId), mh.featureType.eq(fType), mh.matchType.eq(mType), mh.success.eq(true), mh.createdAt.goe(from))
                .fetchOne();
        Long total = queryFactory.select(mh.count()).from(mh)
                .where(mh.project.id.eq(projectId), mh.featureType.eq(fType), mh.matchType.eq(mType), mh.createdAt.goe(from))
                .fetchOne();
        long successCount = Optional.ofNullable(success).orElse(0L);
        long totalCount   = Optional.ofNullable(total).orElse(0L);
        return new DashboardRatiosResult.RatioItem(successCount, totalCount - successCount);
    }

    // ── private 헬퍼: 추이용 (기간 필터 + 문자열 날짜 키) ───────────────────────────

    private Map<String, Long> queryRegistrationByDate(Long projectId, LocalDateTime from, boolean byMonth, boolean byHour, FeatureType featureType) {
        StringTemplate label = byHour
                ? Expressions.stringTemplate("TO_CHAR({0}, 'HH24')",        fh.createdAt)
                : byMonth
                    ? Expressions.stringTemplate("TO_CHAR({0}, 'YYYY-MM')",    fh.createdAt)
                    : Expressions.stringTemplate("TO_CHAR({0}, 'YYYY-MM-DD')", fh.createdAt);
        return queryFactory.select(label, fh.count()).from(fh)
                .where(featureEvents(projectId, from, featureType, FeatureActionType.REGISTER))
                .groupBy(label).fetch().stream()
                .collect(Collectors.toMap(t -> t.get(label), t -> Optional.ofNullable(t.get(fh.count())).orElse(0L)));
    }

    private Map<String, Long> queryMatchByDate(
            Long projectId, LocalDateTime from, boolean byMonth, boolean byHour, FeatureType featureType, MatchType matchType
    ) {
        StringTemplate label = byHour
                ? Expressions.stringTemplate("TO_CHAR({0}, 'HH24')",        mh.createdAt)
                : byMonth
                    ? Expressions.stringTemplate("TO_CHAR({0}, 'YYYY-MM')",    mh.createdAt)
                    : Expressions.stringTemplate("TO_CHAR({0}, 'YYYY-MM-DD')", mh.createdAt);

        return queryFactory.select(label, mh.count()).from(mh)
                .where(mh.project.id.eq(projectId), mh.featureType.eq(featureType), mh.matchType.eq(matchType), mh.createdAt.goe(from))
                .groupBy(label).fetch().stream()
                .collect(Collectors.toMap(t -> t.get(label), t -> Optional.ofNullable(t.get(mh.count())).orElse(0L)));
    }

    private Map<String, Long> queryVerifyByIdByDate(Long projectId, LocalDateTime from, boolean byMonth, boolean byHour) {
        StringTemplate label = byHour
                ? Expressions.stringTemplate("TO_CHAR({0}, 'HH24')",        mh.createdAt)
                : byMonth
                    ? Expressions.stringTemplate("TO_CHAR({0}, 'YYYY-MM')",    mh.createdAt)
                    : Expressions.stringTemplate("TO_CHAR({0}, 'YYYY-MM-DD')", mh.createdAt);

        return queryFactory.select(label, mh.count()).from(mh)
                .where(mh.project.id.eq(projectId), mh.matchType.in(MatchType.VERIFY_ID, MatchType.VERIFY), mh.createdAt.goe(from))
                .groupBy(label).fetch().stream()
                .collect(Collectors.toMap(t -> t.get(label), t -> Optional.ofNullable(t.get(mh.count())).orElse(0L)));
    }

    // ── private 헬퍼: 일일통계용 (전체 기간, LocalDate 키) ──────────────────────────

    private Map<LocalDate, Long> queryAllRegistrationByDate(Long projectId, FeatureType featureType) {
        StringTemplate dateStr = Expressions.stringTemplate("TO_CHAR({0}, 'YYYY-MM-DD')", fh.createdAt);
        return queryFactory.select(dateStr, fh.count()).from(fh)
                .where(featureEvents(projectId, null, featureType, FeatureActionType.REGISTER))
                .groupBy(dateStr).fetch().stream()
                .collect(Collectors.toMap(t -> LocalDate.parse(t.get(dateStr)), t -> Optional.ofNullable(t.get(fh.count())).orElse(0L)));
    }

    private Map<LocalDate, Long> queryAllMatchByDate(Long projectId, FeatureType featureType, MatchType matchType) {
        StringTemplate dateStr = Expressions.stringTemplate("TO_CHAR({0}, 'YYYY-MM-DD')", mh.createdAt);

        return queryFactory.select(dateStr, mh.count()).from(mh)
                .where(mh.project.id.eq(projectId), mh.featureType.eq(featureType), mh.matchType.eq(matchType))
                .groupBy(dateStr).fetch().stream()
                .collect(Collectors.toMap(t -> LocalDate.parse(t.get(dateStr)), t -> Optional.ofNullable(t.get(mh.count())).orElse(0L)));
    }

    private Map<LocalDate, Long> queryAllVerifyByIdByDate(Long projectId) {
        StringTemplate dateStr = Expressions.stringTemplate("TO_CHAR({0}, 'YYYY-MM-DD')", mh.createdAt);

        return queryFactory.select(dateStr, mh.count()).from(mh)
                .where(mh.project.id.eq(projectId), mh.matchType.in(MatchType.VERIFY_ID, MatchType.VERIFY))
                .groupBy(dateStr).fetch().stream()
                .collect(Collectors.toMap(t -> LocalDate.parse(t.get(dateStr)), t -> Optional.ofNullable(t.get(mh.count())).orElse(0L)));
    }

    // ── private 헬퍼: 추이 label 생성 ───────────────────────────────────────────────

    private List<String> generateLabels(TrendPeriod period) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return switch (period) {
            case TODAY -> {
                List<String> labels = new ArrayList<>();
                for (int h = 0; h < 24; h++) {
                    labels.add(String.format("%02d", h));
                }
                yield labels;
            }
            case WEEK -> {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                yield today.minusDays(6).datesUntil(today.plusDays(1))
                        .map(fmt::format)
                        .toList();
            }
            case MONTH -> {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                yield today.minusDays(29).datesUntil(today.plusDays(1))
                        .map(fmt::format)
                        .toList();
            }
            case YEAR -> {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
                List<String> labels = new ArrayList<>();
                LocalDate cursor = today.minusMonths(11).withDayOfMonth(1);
                LocalDate end    = today.withDayOfMonth(1);
                while (!cursor.isAfter(end)) {
                    labels.add(cursor.format(fmt));
                    cursor = cursor.plusMonths(1);
                }
                yield labels;
            }
        };
    }
}

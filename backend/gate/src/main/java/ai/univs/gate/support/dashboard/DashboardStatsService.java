package ai.univs.gate.support.dashboard;

import ai.univs.gate.facade.dashboard.application.result.DashboardDailyStatItemResult;
import ai.univs.gate.facade.dashboard.application.result.DashboardDailyStatsResult;
import ai.univs.gate.facade.dashboard.application.result.DashboardRatiosResult;
import ai.univs.gate.facade.dashboard.application.result.DashboardTrendResult;
import ai.univs.gate.modules.face_feature.domain.enums.FeatureType;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import ai.univs.gate.modules.match.domain.entity.QMatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.face_feature.domain.entity.QFaceFeature;
import ai.univs.gate.modules.palm_feature.domain.entity.QPalmFeature;
import ai.univs.gate.shared.usecase.result.CustomPageResult;
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
    private final QFaceFeature faceFeature = QFaceFeature.faceFeature;
    private final QPalmFeature palmFeature = QPalmFeature.palmFeature;

    public DashboardStatsService(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    // ── 단순 건수 집계 (기간 필터) ─────────────────────────────────────────────────

    public long countRegistrations(Long projectId, LocalDateTime from, FeatureType featureType) {
        if (featureType == FeatureType.PALM) {
            Long count = queryFactory.select(palmFeature.count()).from(palmFeature)
                    .where(palmFeature.project.id.eq(projectId), palmFeature.isDeleted.eq(false), palmFeature.createdAt.goe(from))
                    .fetchOne();
            return Optional.ofNullable(count).orElse(0L);
        }
        else {
            Long count = queryFactory.select(faceFeature.count()).from(faceFeature)
                    .where(faceFeature.project.id.eq(projectId), faceFeature.isDeleted.eq(false), faceFeature.createdAt.goe(from))
                    .fetchOne();
            return Optional.ofNullable(count).orElse(0L);
        }
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

    public long countTotalRegistrations(Long projectId, FeatureType featureType) {
        if (featureType == FeatureType.PALM) {
            Long count = queryFactory.select(palmFeature.count()).from(palmFeature)
                    .where(palmFeature.project.id.eq(projectId), palmFeature.isDeleted.eq(false))
                    .fetchOne();
            return Optional.ofNullable(count).orElse(0L);
        }
        else {
            Long count = queryFactory.select(faceFeature.count()).from(faceFeature)
                    .where(faceFeature.project.id.eq(projectId), faceFeature.isDeleted.eq(false))
                    .fetchOne();
            return Optional.ofNullable(count).orElse(0L);
        }
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

    private DashboardRatiosResult.RatioItem queryRegistrationRatio(Long projectId, LocalDateTime from, FeatureType featureType) {
        if (featureType == FeatureType.PALM) {
            Long active = queryFactory.select(palmFeature.count()).from(palmFeature)
                    .where(palmFeature.project.id.eq(projectId), palmFeature.isDeleted.eq(false), palmFeature.createdAt.goe(from))
                    .fetchOne();
            Long total = queryFactory.select(palmFeature.count()).from(palmFeature)
                    .where(palmFeature.project.id.eq(projectId), palmFeature.createdAt.goe(from))
                    .fetchOne();
            long activeCount = Optional.ofNullable(active).orElse(0L);
            long totalCount  = Optional.ofNullable(total).orElse(0L);
            return new DashboardRatiosResult.RatioItem(activeCount, totalCount - activeCount);
        }

        Long active = queryFactory.select(faceFeature.count()).from(faceFeature)
                .where(faceFeature.project.id.eq(projectId), faceFeature.isDeleted.eq(false), faceFeature.createdAt.goe(from))
                .fetchOne();
        Long total = queryFactory.select(faceFeature.count()).from(faceFeature)
                .where(faceFeature.project.id.eq(projectId), faceFeature.createdAt.goe(from))
                .fetchOne();
        long activeCount = Optional.ofNullable(active).orElse(0L);
        long totalCount  = Optional.ofNullable(total).orElse(0L);
        return new DashboardRatiosResult.RatioItem(activeCount, totalCount - activeCount);
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
        if (featureType == FeatureType.PALM) {
            StringTemplate label = byHour
                    ? Expressions.stringTemplate("TO_CHAR({0}, 'HH24')",        palmFeature.createdAt)
                    : byMonth
                        ? Expressions.stringTemplate("TO_CHAR({0}, 'YYYY-MM')",    palmFeature.createdAt)
                        : Expressions.stringTemplate("TO_CHAR({0}, 'YYYY-MM-DD')", palmFeature.createdAt);
            return queryFactory.select(label, palmFeature.count()).from(palmFeature)
                    .where(palmFeature.project.id.eq(projectId), palmFeature.isDeleted.eq(false), palmFeature.createdAt.goe(from))
                    .groupBy(label).fetch().stream()
                    .collect(Collectors.toMap(t -> t.get(label), t -> Optional.ofNullable(t.get(palmFeature.count())).orElse(0L)));
        }

        StringTemplate label = byHour
                ? Expressions.stringTemplate("TO_CHAR({0}, 'HH24')",        faceFeature.createdAt)
                : byMonth
                    ? Expressions.stringTemplate("TO_CHAR({0}, 'YYYY-MM')",    faceFeature.createdAt)
                    : Expressions.stringTemplate("TO_CHAR({0}, 'YYYY-MM-DD')", faceFeature.createdAt);
        return queryFactory.select(label, faceFeature.count()).from(faceFeature)
                .where(faceFeature.project.id.eq(projectId), faceFeature.isDeleted.eq(false), faceFeature.createdAt.goe(from))
                .groupBy(label).fetch().stream()
                .collect(Collectors.toMap(t -> t.get(label), t -> Optional.ofNullable(t.get(faceFeature.count())).orElse(0L)));
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
        if (featureType == FeatureType.PALM) {
            StringTemplate dateStr = Expressions.stringTemplate("TO_CHAR({0}, 'YYYY-MM-DD')", palmFeature.createdAt);
            return queryFactory.select(dateStr, palmFeature.count()).from(palmFeature)
                    .where(palmFeature.project.id.eq(projectId), palmFeature.isDeleted.eq(false))
                    .groupBy(dateStr).fetch().stream()
                    .collect(Collectors.toMap(t -> LocalDate.parse(t.get(dateStr)), t -> Optional.ofNullable(t.get(palmFeature.count())).orElse(0L)));
        }

        StringTemplate dateStr = Expressions.stringTemplate("TO_CHAR({0}, 'YYYY-MM-DD')", faceFeature.createdAt);
        return queryFactory.select(dateStr, faceFeature.count()).from(faceFeature)
                .where(faceFeature.project.id.eq(projectId), faceFeature.isDeleted.eq(false))
                .groupBy(dateStr).fetch().stream()
                .collect(Collectors.toMap(t -> LocalDate.parse(t.get(dateStr)), t -> Optional.ofNullable(t.get(faceFeature.count())).orElse(0L)));
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

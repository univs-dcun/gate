package ai.univs.gate.support.dashboard;

import ai.univs.gate.facade.dashboard.application.result.DashboardDailyStatItemResult;
import ai.univs.gate.facade.dashboard.application.result.DashboardDailyStatsResult;
import ai.univs.gate.facade.dashboard.application.result.DashboardRatiosResult;
import ai.univs.gate.facade.dashboard.application.result.DashboardTrendResult;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import ai.univs.gate.modules.match.domain.entity.QMatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.user.domain.entity.QUser;
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
    private final QUser user = QUser.user;

    public DashboardStatsService(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    // ── 단순 건수 집계 ──────────────────────────────────────────────────────────────

    public long countRegistrations(Long projectId, LocalDateTime from) {
        Long count = queryFactory
                .select(user.count())
                .from(user)
                .where(user.project.id.eq(projectId),
                       user.isDeleted.eq(false),
                       user.createdAt.goe(from))
                .fetchOne();
        return Optional.ofNullable(count).orElse(0L);
    }

    public long countVerifyById(Long projectId, LocalDateTime from) {
        Long count = queryFactory
                .select(mh.count())
                .from(mh)
                .where(mh.project.id.eq(projectId),
                       mh.matchType.in(MatchType.VERIFY_ID, MatchType.VERIFY),
                       mh.createdAt.goe(from))
                .fetchOne();
        return Optional.ofNullable(count).orElse(0L);
    }

    public long countVerifyByImage(Long projectId, LocalDateTime from) {
        Long count = queryFactory
                .select(mh.count())
                .from(mh)
                .where(mh.project.id.eq(projectId),
                       mh.matchType.eq(MatchType.VERIFY_IMAGE),
                       mh.createdAt.goe(from))
                .fetchOne();
        return Optional.ofNullable(count).orElse(0L);
    }

    public long countIdentify(Long projectId, LocalDateTime from) {
        Long count = queryFactory
                .select(mh.count())
                .from(mh)
                .where(mh.project.id.eq(projectId),
                       mh.matchType.eq(MatchType.IDENTIFY),
                       mh.createdAt.goe(from))
                .fetchOne();
        return Optional.ofNullable(count).orElse(0L);
    }

    public long countLiveness(Long projectId, LocalDateTime from) {
        Long count = queryFactory
                .select(mh.count())
                .from(mh)
                .where(mh.project.id.eq(projectId),
                       mh.matchType.eq(MatchType.LIVENESS),
                       mh.createdAt.goe(from))
                .fetchOne();
        return Optional.ofNullable(count).orElse(0L);
    }

    // ── 비율 통계 ────────────────────────────────────────────────────────────────────

    public DashboardRatiosResult getRatios(Long projectId, LocalDateTime from) {
        return new DashboardRatiosResult(
                queryRegistrationRatio(projectId, from),
                queryVerifyByIdRatio(projectId, from),
                queryMatchRatio(projectId, from, MatchType.VERIFY_IMAGE),
                queryMatchRatio(projectId, from, MatchType.IDENTIFY),
                queryMatchRatio(projectId, from, MatchType.LIVENESS)
        );
    }

    // ── 사용량 추이 ─────────────────────────────────────────────────────────────────

    public DashboardTrendResult getTrend(Long projectId, TrendPeriod period) {
        boolean byMonth = period == TrendPeriod.YEAR;

        LocalDateTime from = switch (period) {
            case WEEK  -> LocalDate.now(ZoneOffset.UTC).minusDays(6).atStartOfDay();
            case MONTH -> LocalDate.now(ZoneOffset.UTC).minusDays(29).atStartOfDay();
            case YEAR  -> LocalDate.now(ZoneOffset.UTC).minusMonths(11).withDayOfMonth(1).atStartOfDay();
        };

        List<String> labels = generateLabels(period);

        Map<String, Long> regMap      = queryRegistrationByDate(projectId, from, byMonth);
        Map<String, Long> verByIdMap  = queryVerifyByIdByDate(projectId, from, byMonth);
        Map<String, Long> verByImgMap = queryMatchByDate(projectId, from, byMonth, MatchType.VERIFY_IMAGE);
        Map<String, Long> idnMap      = queryMatchByDate(projectId, from, byMonth, MatchType.IDENTIFY);
        Map<String, Long> livMap      = queryMatchByDate(projectId, from, byMonth, MatchType.LIVENESS);

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

    public DashboardDailyStatsResult getDailyStats(Long projectId, int page, int pageSize) {
        Map<LocalDate, Long> regMap      = queryAllRegistrationByDate(projectId);
        Map<LocalDate, Long> verByIdMap  = queryAllVerifyByIdByDate(projectId);
        Map<LocalDate, Long> verByImgMap = queryAllMatchByDate(projectId, MatchType.VERIFY_IMAGE);
        Map<LocalDate, Long> idnMap      = queryAllMatchByDate(projectId, MatchType.IDENTIFY);
        Map<LocalDate, Long> livMap      = queryAllMatchByDate(projectId, MatchType.LIVENESS);

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
            case WEEK  -> LocalDate.now(ZoneOffset.UTC).minusDays(6).atStartOfDay();
            case MONTH -> LocalDate.now(ZoneOffset.UTC).minusDays(29).atStartOfDay();
            case YEAR  -> LocalDate.now(ZoneOffset.UTC).minusMonths(11).withDayOfMonth(1).atStartOfDay();
        };
    }

    // ── private 헬퍼: 비율 집계 ─────────────────────────────────────────────────────

    private DashboardRatiosResult.RatioItem queryRegistrationRatio(Long projectId, LocalDateTime from) {
        Long active = queryFactory
                .select(user.count())
                .from(user)
                .where(user.project.id.eq(projectId),
                       user.isDeleted.eq(false),
                       user.createdAt.goe(from))
                .fetchOne();

        Long total = queryFactory
                .select(user.count())
                .from(user)
                .where(user.project.id.eq(projectId),
                       user.createdAt.goe(from))
                .fetchOne();

        long activeCount = Optional.ofNullable(active).orElse(0L);
        long totalCount  = Optional.ofNullable(total).orElse(0L);
        return new DashboardRatiosResult.RatioItem(activeCount, totalCount - activeCount);
    }

    private DashboardRatiosResult.RatioItem queryVerifyByIdRatio(Long projectId, LocalDateTime from) {
        Long success = queryFactory
                .select(mh.count())
                .from(mh)
                .where(mh.project.id.eq(projectId),
                       mh.matchType.in(MatchType.VERIFY_ID, MatchType.VERIFY),
                       mh.success.eq(true),
                       mh.createdAt.goe(from))
                .fetchOne();

        Long total = queryFactory
                .select(mh.count())
                .from(mh)
                .where(mh.project.id.eq(projectId),
                       mh.matchType.in(MatchType.VERIFY_ID, MatchType.VERIFY),
                       mh.createdAt.goe(from))
                .fetchOne();

        long successCount = Optional.ofNullable(success).orElse(0L);
        long totalCount   = Optional.ofNullable(total).orElse(0L);
        return new DashboardRatiosResult.RatioItem(successCount, totalCount - successCount);
    }

    private DashboardRatiosResult.RatioItem queryMatchRatio(Long projectId, LocalDateTime from, MatchType type) {
        Long success = queryFactory
                .select(mh.count())
                .from(mh)
                .where(mh.project.id.eq(projectId),
                       mh.matchType.eq(type),
                       mh.success.eq(true),
                       mh.createdAt.goe(from))
                .fetchOne();

        Long total = queryFactory
                .select(mh.count())
                .from(mh)
                .where(mh.project.id.eq(projectId),
                       mh.matchType.eq(type),
                       mh.createdAt.goe(from))
                .fetchOne();

        long successCount = Optional.ofNullable(success).orElse(0L);
        long totalCount   = Optional.ofNullable(total).orElse(0L);
        return new DashboardRatiosResult.RatioItem(successCount, totalCount - successCount);
    }

    // ── private 헬퍼: 추이용 (기간 필터 + 문자열 날짜 키) ───────────────────────────

    private Map<String, Long> queryRegistrationByDate(Long projectId, LocalDateTime from, boolean byMonth) {
        StringTemplate label = byMonth
                ? Expressions.stringTemplate("DATE_FORMAT({0}, '%Y-%m')",    user.createdAt)
                : Expressions.stringTemplate("DATE_FORMAT({0}, '%Y-%m-%d')", user.createdAt);

        return queryFactory
                .select(label, user.count())
                .from(user)
                .where(user.project.id.eq(projectId),
                       user.isDeleted.eq(false),
                       user.createdAt.goe(from))
                .groupBy(label)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        t -> t.get(label),
                        t -> Optional.ofNullable(t.get(user.count())).orElse(0L)
                ));
    }

    private Map<String, Long> queryMatchByDate(
            Long projectId, LocalDateTime from, boolean byMonth, MatchType type
    ) {
        StringTemplate label = byMonth
                ? Expressions.stringTemplate("DATE_FORMAT({0}, '%Y-%m')",    mh.createdAt)
                : Expressions.stringTemplate("DATE_FORMAT({0}, '%Y-%m-%d')", mh.createdAt);

        return queryFactory
                .select(label, mh.count())
                .from(mh)
                .where(mh.project.id.eq(projectId),
                       mh.matchType.eq(type),
                       mh.createdAt.goe(from))
                .groupBy(label)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        t -> t.get(label),
                        t -> Optional.ofNullable(t.get(mh.count())).orElse(0L)
                ));
    }

    private Map<String, Long> queryVerifyByIdByDate(Long projectId, LocalDateTime from, boolean byMonth) {
        StringTemplate label = byMonth
                ? Expressions.stringTemplate("DATE_FORMAT({0}, '%Y-%m')",    mh.createdAt)
                : Expressions.stringTemplate("DATE_FORMAT({0}, '%Y-%m-%d')", mh.createdAt);

        return queryFactory
                .select(label, mh.count())
                .from(mh)
                .where(mh.project.id.eq(projectId),
                       mh.matchType.in(MatchType.VERIFY_ID, MatchType.VERIFY),
                       mh.createdAt.goe(from))
                .groupBy(label)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        t -> t.get(label),
                        t -> Optional.ofNullable(t.get(mh.count())).orElse(0L)
                ));
    }

    // ── private 헬퍼: 일일통계용 (전체 기간, LocalDate 키) ──────────────────────────

    private Map<LocalDate, Long> queryAllRegistrationByDate(Long projectId) {
        StringTemplate dateStr =
                Expressions.stringTemplate("DATE_FORMAT({0}, '%Y-%m-%d')", user.createdAt);

        return queryFactory
                .select(dateStr, user.count())
                .from(user)
                .where(user.project.id.eq(projectId), user.isDeleted.eq(false))
                .groupBy(dateStr)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        t -> LocalDate.parse(t.get(dateStr)),
                        t -> Optional.ofNullable(t.get(user.count())).orElse(0L)
                ));
    }

    private Map<LocalDate, Long> queryAllMatchByDate(Long projectId, MatchType type) {
        StringTemplate dateStr =
                Expressions.stringTemplate("DATE_FORMAT({0}, '%Y-%m-%d')", mh.createdAt);

        return queryFactory
                .select(dateStr, mh.count())
                .from(mh)
                .where(mh.project.id.eq(projectId), mh.matchType.eq(type))
                .groupBy(dateStr)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        t -> LocalDate.parse(t.get(dateStr)),
                        t -> Optional.ofNullable(t.get(mh.count())).orElse(0L)
                ));
    }

    private Map<LocalDate, Long> queryAllVerifyByIdByDate(Long projectId) {
        StringTemplate dateStr =
                Expressions.stringTemplate("DATE_FORMAT({0}, '%Y-%m-%d')", mh.createdAt);

        return queryFactory
                .select(dateStr, mh.count())
                .from(mh)
                .where(mh.project.id.eq(projectId),
                       mh.matchType.in(MatchType.VERIFY_ID, MatchType.VERIFY))
                .groupBy(dateStr)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        t -> LocalDate.parse(t.get(dateStr)),
                        t -> Optional.ofNullable(t.get(mh.count())).orElse(0L)
                ));
    }

    // ── private 헬퍼: 추이 label 생성 ───────────────────────────────────────────────

    private List<String> generateLabels(TrendPeriod period) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return switch (period) {
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

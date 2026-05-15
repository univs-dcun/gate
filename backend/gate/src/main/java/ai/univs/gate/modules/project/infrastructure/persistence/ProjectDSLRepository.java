package ai.univs.gate.modules.project.infrastructure.persistence;

import ai.univs.gate.modules.api_key.domain.entity.QApiKey;
import ai.univs.gate.modules.match.domain.entity.QMatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.project.application.input.ProjectQuery;
import ai.univs.gate.modules.project.application.result.ProjectSummaryResult;
import ai.univs.gate.modules.project.domain.entity.QProject;
import ai.univs.gate.modules.user.domain.entity.QUser;
import ai.univs.gate.shared.utils.CustomPageable;
import com.querydsl.core.Tuple;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Repository
public class ProjectDSLRepository {

    private final JPAQueryFactory queryFactory;

    private final QProject project = QProject.project;
    private final QUser user = QUser.user;
    private final QMatchHistory matchHistory = QMatchHistory.matchHistory;
    private final QApiKey qApiKey = QApiKey.apiKey1;

    public ProjectDSLRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    public Page<ProjectSummaryResult> findByAccountIdAndIsDeletedFalse(ProjectQuery query) {
        Pageable pageable = CustomPageable.of(query.page(), query.pageSize());
        var booleanBuilder = createBooleanBuilder(query);

        var userCount = JPAExpressions.select(user.count())
                .from(user)
                .where(user.project.id.eq(project.id).and(user.isDeleted.isFalse()));

        var verifyByIdCount = JPAExpressions.select(matchHistory.count())
                .from(matchHistory)
                .where(matchHistory.project.id.eq(project.id)
                        .and(matchHistory.matchType.in(MatchType.VERIFY_ID, MatchType.VERIFY)));

        var verifyByImageCount = JPAExpressions.select(matchHistory.count())
                .from(matchHistory)
                .where(matchHistory.project.id.eq(project.id)
                        .and(matchHistory.matchType.eq(MatchType.VERIFY_IMAGE)));

        var identifyCount = JPAExpressions.select(matchHistory.count())
                .from(matchHistory)
                .where(matchHistory.project.id.eq(project.id)
                        .and(matchHistory.matchType.eq(MatchType.IDENTIFY)));

        var livenessCount = JPAExpressions.select(matchHistory.count())
                .from(matchHistory)
                .where(matchHistory.project.id.eq(project.id)
                        .and(matchHistory.matchType.eq(MatchType.LIVENESS)));

        List<Tuple> rows = queryFactory
                .select(
                        project.id,
                        project.projectName,
                        project.projectDescription,
                        project.status,
                        project.projectType,
                        project.projectModuleType,
                        project.packageKey,
                        userCount,
                        verifyByIdCount,
                        verifyByImageCount,
                        identifyCount,
                        livenessCount,
                        project.createdAt,
                        project.updatedAt,
                        qApiKey.apiKey)
                .from(project)
                .leftJoin(qApiKey).on(qApiKey.project.id.eq(project.id).and(qApiKey.isActive.isTrue()))
                .where(booleanBuilder)
                .orderBy(createOrderSpecifiers().toArray(new OrderSpecifier[0]))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        List<ProjectSummaryResult> content = rows.stream()
                .map(t -> new ProjectSummaryResult(
                        t.get(project.id),
                        t.get(project.projectName),
                        t.get(project.projectDescription),
                        t.get(project.status),
                        t.get(project.projectType),
                        t.get(project.projectModuleType),
                        t.get(project.packageKey),
                        t.get(userCount),
                        t.get(verifyByIdCount),
                        t.get(verifyByImageCount),
                        t.get(identifyCount),
                        t.get(livenessCount),
                        t.get(project.createdAt),
                        t.get(project.updatedAt),
                        t.get(qApiKey.apiKey)
                ))
                .toList();

        Long total = queryFactory
                .select(project.count())
                .from(project)
                .where(booleanBuilder)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private List<OrderSpecifier<?>> createOrderSpecifiers() {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();
        orderSpecifiers.add(new OrderSpecifier<>(Order.DESC, project.createdAt));
        return orderSpecifiers;
    }

    private BooleanBuilder createBooleanBuilder(ProjectQuery query) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();

        booleanBuilder.and(project.accountId.eq(query.accountId()));
        booleanBuilder.and(project.isDeleted.isFalse());

        if (StringUtils.hasText(query.projectKeyword())) {
            BooleanBuilder keywordBuilder = new BooleanBuilder();
            keywordBuilder.or(project.projectName.containsIgnoreCase(query.projectKeyword()));
            keywordBuilder.or(project.projectDescription.containsIgnoreCase(query.projectKeyword()));
            booleanBuilder.and(keywordBuilder);
        }

        return booleanBuilder;
    }
}

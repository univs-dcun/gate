package ai.univs.gate.modules.project.infrastructure.persistence;

import ai.univs.gate.modules.api_key.domain.entity.QApiKey;
import ai.univs.gate.modules.feature.domain.entity.QBiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.entity.QMatchHistory;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.project.application.input.ProjectQuery;
import ai.univs.gate.modules.project.application.result.ProjectSummaryResult;
import ai.univs.gate.modules.project.domain.entity.QProject;
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
    private final QBiometricFeature bf = QBiometricFeature.biometricFeature;
    private final QMatchHistory matchHistory = QMatchHistory.matchHistory;
    private final QApiKey qApiKey = QApiKey.apiKey1;

    public ProjectDSLRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    public Page<ProjectSummaryResult> findByAccountIdAndIsDeletedFalse(ProjectQuery query) {
        Pageable pageable = CustomPageable.of(query.page(), query.pageSize());
        var booleanBuilder = createBooleanBuilder(query);

        // Face counts
        var faceRegistrationCount = JPAExpressions.select(bf.count())
                .from(bf)
                .where(bf.project.id.eq(project.id).and(bf.type.eq(FeatureType.FACE)).and(bf.isDeleted.isFalse()));

        var faceVerifyByIdCount = JPAExpressions.select(matchHistory.count())
                .from(matchHistory)
                .where(matchHistory.project.id.eq(project.id)
                        .and(matchHistory.matchType.in(MatchType.VERIFY_ID, MatchType.VERIFY))
                        .and(matchHistory.featureType.eq(FeatureType.FACE)));

        var faceVerifyByImageCount = JPAExpressions.select(matchHistory.count())
                .from(matchHistory)
                .where(matchHistory.project.id.eq(project.id)
                        .and(matchHistory.matchType.eq(MatchType.VERIFY_IMAGE))
                        .and(matchHistory.featureType.eq(FeatureType.FACE)));

        var faceIdentifyCount = JPAExpressions.select(matchHistory.count())
                .from(matchHistory)
                .where(matchHistory.project.id.eq(project.id)
                        .and(matchHistory.matchType.eq(MatchType.IDENTIFY))
                        .and(matchHistory.featureType.eq(FeatureType.FACE)));

        var faceLivenessCount = JPAExpressions.select(matchHistory.count())
                .from(matchHistory)
                .where(matchHistory.project.id.eq(project.id)
                        .and(matchHistory.matchType.eq(MatchType.LIVENESS))
                        .and(matchHistory.featureType.eq(FeatureType.FACE)));

        // Palm counts
        var palmRegistrationCount = JPAExpressions.select(bf.count())
                .from(bf)
                .where(bf.project.id.eq(project.id).and(bf.type.eq(FeatureType.PALM)).and(bf.isDeleted.isFalse()));

        var palmIdentifyCount = JPAExpressions.select(matchHistory.count())
                .from(matchHistory)
                .where(matchHistory.project.id.eq(project.id)
                        .and(matchHistory.matchType.eq(MatchType.IDENTIFY))
                        .and(matchHistory.featureType.eq(FeatureType.PALM)));

        var palmLivenessCount = JPAExpressions.select(matchHistory.count())
                .from(matchHistory)
                .where(matchHistory.project.id.eq(project.id)
                        .and(matchHistory.matchType.eq(MatchType.LIVENESS))
                        .and(matchHistory.featureType.eq(FeatureType.PALM)));

        List<Tuple> rows = queryFactory
                .select(
                        project.id,
                        project.projectName,
                        project.projectDescription,
                        project.colorTag,
                        project.status,
                        faceRegistrationCount,
                        faceVerifyByIdCount,
                        faceVerifyByImageCount,
                        faceIdentifyCount,
                        faceLivenessCount,
                        palmRegistrationCount,
                        palmIdentifyCount,
                        palmLivenessCount,
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
                        t.get(project.colorTag),
                        t.get(project.status),
                        t.get(faceRegistrationCount),
                        t.get(faceVerifyByIdCount),
                        t.get(faceVerifyByImageCount),
                        t.get(faceIdentifyCount),
                        t.get(faceLivenessCount),
                        t.get(palmRegistrationCount),
                        t.get(palmIdentifyCount),
                        t.get(palmLivenessCount),
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

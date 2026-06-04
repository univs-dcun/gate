package ai.univs.gate.modules.face_media.infrastructure.persistence;

import ai.univs.gate.modules.face_media.application.input.FaceMediaQuery;
import ai.univs.gate.modules.face_media.domain.entity.FaceMedia;
import ai.univs.gate.modules.face_media.domain.entity.QFaceMedia;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.utils.CustomPageable;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class FaceMediaDSLRepository {

    private final JPAQueryFactory queryFactory;

    public FaceMediaDSLRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    private final QFaceMedia faceMedia = QFaceMedia.faceMedia;

    public Page<FaceMedia> findAllByQuery(FaceMediaQuery query, Long projectId) {
        List<OrderSpecifier<?>> orderSpecifiers = buildOrderSpecifiers();
        BooleanBuilder booleanBuilder = buildConditionBuilder(query, projectId);

        Pageable pageable = CustomPageable.of(query.page(), query.pageSize());

        List<FaceMedia> fetch = queryFactory
                .selectFrom(faceMedia)
                .where(booleanBuilder)
                .orderBy(orderSpecifiers.toArray(new OrderSpecifier[0]))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(faceMedia.count())
                .from(faceMedia)
                .where(booleanBuilder)
                .fetchOne();

        return new PageImpl<>(fetch, pageable, total);
    }

    private List<OrderSpecifier<?>> buildOrderSpecifiers() {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();
        orderSpecifiers.add(new OrderSpecifier<>(Order.ASC, faceMedia.id));
        return orderSpecifiers;
    }

    private BooleanBuilder buildConditionBuilder(FaceMediaQuery query, Long projectId) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();

        booleanBuilder.and(faceMedia.project.id.eq(projectId));

        if (query.keyword() != null && !query.keyword().isEmpty()) {
            BooleanBuilder keywordBuilder = new BooleanBuilder();
            keywordBuilder.or(faceMedia.faceId.containsIgnoreCase(query.keyword()));
            keywordBuilder.or(faceMedia.description.containsIgnoreCase(query.keyword()));
            keywordBuilder.or(faceMedia.transactionUuid.containsIgnoreCase(query.keyword()));
            booleanBuilder.and(keywordBuilder);
        }

        booleanBuilder.and(faceMedia.isDeleted.eq(false));

        UserContext userContext = UserContext.get();
        if (query.hasDate()) {
            booleanBuilder.and(faceMedia.createdAt.goe(query.getStartDateTime(userContext.getTimezone())));
            booleanBuilder.and(faceMedia.createdAt.loe(query.getEndDateTime(userContext.getTimezone())));
        }

        return booleanBuilder;
    }
}

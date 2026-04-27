package ai.univs.gate.modules.user.infrastructure.persistence;

import ai.univs.gate.modules.user.application.input.UserQuery;
import ai.univs.gate.modules.user.domain.entity.QUser;
import ai.univs.gate.modules.user.domain.entity.User;
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
public class UserDSLRepository {

    private final JPAQueryFactory queryFactory;

    public UserDSLRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    private final QUser user = QUser.user;

    public Page<User> findAllByQuery(UserQuery query, Long projectId) {
        List<OrderSpecifier<?>> orderSpecifiers = buildOrderSpecifiers();
        BooleanBuilder booleanBuilder = buildConditionBuilder(query, projectId);

        List<User> fetch = queryFactory
                .selectFrom(user)
                .where(booleanBuilder)
                .orderBy(orderSpecifiers.toArray(new OrderSpecifier[0]))
                .fetch();

        Long total = queryFactory
                .select(user.count())
                .from(user)
                .where(booleanBuilder)
                .fetchOne();

        Pageable pageable = CustomPageable.of(query.page(), query.pageSize());

        return new PageImpl<>(fetch, pageable, total);
    }

    private List<OrderSpecifier<?>> buildOrderSpecifiers() {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();
        orderSpecifiers.add(new OrderSpecifier<>(Order.ASC, user.id)); // 기본 정렬
        return orderSpecifiers;
    }

    private BooleanBuilder buildConditionBuilder(UserQuery query, Long projectId) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();

        booleanBuilder.and(user.project.id.eq(projectId));

        if (query.userKeyword() != null && !query.userKeyword().isEmpty()) {
            BooleanBuilder keywordBuilder = new BooleanBuilder();
            keywordBuilder.or(user.faceId.containsIgnoreCase(query.userKeyword()));
            keywordBuilder.or(user.description.containsIgnoreCase(query.userKeyword()));
            keywordBuilder.or(user.transactionUuid.containsIgnoreCase(query.userKeyword()));
            booleanBuilder.and(keywordBuilder);
        }

        booleanBuilder.and(user.isDeleted.eq(false));

        UserContext userContext = UserContext.get();
        if (query.hasDate()) {
            booleanBuilder.and(user.createdAt.goe(query.getStartDateTime(userContext.getTimezone())));
            booleanBuilder.and(user.createdAt.loe(query.getEndDateTime(userContext.getTimezone())));
        }

        return booleanBuilder;
    }
}

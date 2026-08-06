package ai.univs.gate.shared.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.facade.dashboard.application.usecase.GetDashboardSummaryUseCase;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.application.usecase.DeleteProjectUseCase;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * UG-288: 트랜잭션 선언이 조용히 사라지는 것을 막는 가드.
 *
 * <p>반박 리뷰가 찾은 생존 변이다. {@code DeleteProjectUseCase.execute} 에서
 * {@code @Transactional} 을 떼거나 {@code readOnly = true} 로 바꾸면 더티 체킹이 flush 되지 않아
 * <b>UG-288 과 똑같은 실패 모드</b>가 재현된다 — 삭제 API 가 200 을 주면서 아무것도 바꾸지 않는다.
 * 그런데 {@code DeleteProjectUseCaseTest} 는 순수 Mockito 테스트라 엔티티 객체의 필드 변화만 보고,
 * 그 변화가 커밋되는지는 보지 못한다. 두 변이 모두 초록으로 통과했다.
 *
 * <p>제대로 된 답은 JPA 슬라이스 테스트다 (UG-300). 그 인프라가 생기기 전까지, 최소한 애노테이션이
 * 조용히 사라지는 것은 여기서 막는다. 트랜잭션 경계가 실제로 동작하는지는 여전히 검증하지 못한다 —
 * 이 테스트가 지키는 것은 "선언이 남아 있는가" 까지다.
 *
 * <p><b>읽기 쪽도 함께 지킨다</b> (2차 리뷰 지적). 처음에는 삭제 경로만 봤는데, 같은 PR 이
 * {@code GetDashboardSummaryUseCase} 에도 {@code @Transactional(readOnly = true)} 를 <b>같은
 * 이유로</b> 새로 붙이고는 가드를 두지 않았다. 그쪽이 사라지면 나는 증상은 반대다 — flush 가
 * 아니라 지연 로딩이다. {@code ApiKey.project} 가 LAZY 이고 {@code ApiKeyService} 에도 트랜잭션이
 * 없어서, 지금은 OSIV 기본값(true)에만 기대고 있다. {@code application.yaml} 의
 * {@code open-in-view: false} 는 {@code local} 프로파일 블록 안에만 있다. 누가 그걸 전역으로
 * 올리는 순간 이 엔드포인트만 {@code LazyInitializationException} 으로 500 이 된다.
 *
 * <p>여러 모듈을 가로질러 보므로 {@code shared} 아래 둔다. 처음에는 project 모듈 안에
 * {@code DeleteProjectTransactionGuardTest} 라는 이름으로 있었는데, 대시보드까지 보게 된 뒤로는
 * 이름도 자리도 내용과 맞지 않았다 (3차 리뷰 지적).
 */
@DisplayName("UG-288: 트랜잭션 선언 가드")
class TransactionDeclarationGuardTest {

    /**
     * 트랜잭션이 실제로 열린다고 볼 수 있는 전파 속성.
     *
     * <p>{@code MANDATORY} 는 뺐다 (2차 리뷰 지적). 바깥 트랜잭션이 없으면
     * {@code IllegalTransactionStateException} 이라 요청이 전부 500 이 된다 — 이 UseCase 들은
     * 컨트롤러에서 직접 불리므로 바깥 트랜잭션이 없다. "쓰기가 커밋된다" 를 지키는 가드가
     * "요청이 전부 실패한다" 를 통과시키면 안 된다.
     */
    private static final Propagation[] 트랜잭션이_열리는_전파 = {
            Propagation.REQUIRED, Propagation.REQUIRES_NEW
    };

    /**
     * 클래스 레벨 선언도 인정한다 (3차 리뷰 지적).
     *
     * <p>{@code Method#getAnnotation} 만 쓰면 {@code @Transactional} 을 클래스로 올리는 순간 —
     * 스프링은 동일하게 처리하는 리팩터링인데도 — 이 가드가 거짓으로 실패한다.
     *
     * <p>메서드를 먼저 보고 없으면 선언 클래스를 본다. 스프링의
     * {@code AnnotationTransactionAttributeSource} 와 같은 우선순위다 —
     * {@code AnnotatedElementUtils} 는 그 폴백을 대신 해 주지 않는다.
     */
    private static Transactional transactionalOf(Method method) {
        Transactional onMethod =
                AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
        if (onMethod != null) {
            return onMethod;
        }
        return AnnotatedElementUtils.findMergedAnnotation(
                method.getDeclaringClass(), Transactional.class);
    }

    @Test
    @DisplayName("삭제 execute 는 쓰기 트랜잭션이어야 한다")
    void 삭제는_쓰기_트랜잭션이다() throws NoSuchMethodException {
        Method execute = DeleteProjectUseCase.class.getMethod("execute", Long.class, Long.class);
        Transactional transactional = transactionalOf(execute);

        assertThat(transactional)
                .as("@Transactional 이 없으면 project.delete() 와 키 비활성화가 flush 되지 않는다 — "
                        + "삭제 API 가 200 을 주면서 아무 일도 하지 않게 된다 (UG-288 원래 증상)")
                .isNotNull();

        assertThat(transactional.readOnly())
                .as("readOnly = true 면 Hibernate 가 FlushMode.MANUAL 로 내려가 더티 체킹 결과가 "
                        + "커밋되지 않는다. 위와 같은 증상이다")
                .isFalse();

        assertThat(transactional.propagation())
                .as("트랜잭션 없이도 실행될 수 있거나, 바깥 트랜잭션을 요구하는 전파 속성이면 안 된다")
                .isIn((Object[]) 트랜잭션이_열리는_전파);
    }

    @Test
    @DisplayName("대시보드 summary execute 는 읽기 트랜잭션이어야 한다")
    void 대시보드_summary_는_읽기_트랜잭션이다() throws NoSuchMethodException {
        Method execute = GetDashboardSummaryUseCase.class.getMethod(
                "execute", Long.class, String.class, TrendPeriod.class, FeatureType.class);
        Transactional transactional = transactionalOf(execute);

        assertThat(transactional)
                .as("@Transactional 이 없으면 ApiKey.project(LAZY) 접근이 영속성 컨텍스트 밖에서 일어난다. "
                        + "지금은 OSIV 기본값(true)이 가려주고 있을 뿐이고, open-in-view: false 가 "
                        + "local 프로파일 밖으로 나오는 순간 이 엔드포인트만 500 이 된다")
                .isNotNull();

        assertThat(transactional.readOnly())
                .as("읽기 전용 선언이 사라져도 오늘 당장은 동작한다. 그래도 못박는 이유는 두 가지다 — "
                        + "집계만 하는 경로에서 더티 체킹 flush 가 일어날 여지를 없애고, 나중에 읽기 "
                        + "복제본 라우팅을 붙일 때 이 선언이 판정 기준이 되기 때문이다 (3차 리뷰 지적)")
                .isTrue();

        assertThat(transactional.propagation())
                .as("트랜잭션 없이도 실행될 수 있거나, 바깥 트랜잭션을 요구하는 전파 속성이면 "
                        + "위와 같은 문제가 난다")
                .isIn((Object[]) 트랜잭션이_열리는_전파);
    }
}

package ai.univs.gate.shared.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.facade.dashboard.application.usecase.GetDashboardSummaryUseCase;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.application.usecase.DeleteProjectUseCase;
import ai.univs.gate.support.api_key.ApiKeyService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * 아니라 지연 로딩이다. {@code ApiKey.project} 가 LAZY 라 트랜잭션 밖에서 건드리면
 * {@code LazyInitializationException} 으로 500 이 된다.
 *
 * <p>(이 문단은 "지금은 OSIV 기본값(true)에만 기대고 있다" 로 시작했다. UG-335 가
 * {@code open-in-view: false} 를 전역으로 올렸고, {@code ApiKeyService} 는 이제 자기
 * 트랜잭션을 연다. 그 두 가지를 아래에서 함께 지킨다.)
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

    /**
     * {@code ApiKeyService} 는 자기 지연 로딩의 경계를 자기가 연다 (UG-335).
     *
     * <p>{@code validateOwnership} 이 {@code apiKey.getProject().getAccountId()} 로 프록시를
     * 초기화한다. 예전에는 그 책임이 호출자에게 있었고, 트랜잭션 없는 유스케이스
     * ({@code ExtractUseCase}, {@code GetFeatureListUseCase})는 OSIV 기본값에만 기대고 있었다.
     *
     * <p>호출자마다 선언을 붙이지 않은 이유는 {@code ExtractUseCase} 다. 그쪽은 조회 직후
     * face 서비스를 Feign 으로 부르므로, 트랜잭션으로 감싸면 원격 호출 내내 DB 커넥션을
     * 붙든다 — OSIV 를 끄려던 이유와 똑같은 문제를 다른 자리에 만드는 셈이다.
     */
    @Test
    @DisplayName("ApiKeyService 는 읽기 트랜잭션을 연다 — 지연 로딩 경계의 소유자다")
    void ApiKeyService_는_읽기_트랜잭션이다() {
        Transactional transactional =
                AnnotatedElementUtils.findMergedAnnotation(ApiKeyService.class, Transactional.class);

        assertThat(transactional)
                .as("이 선언이 사라지면 트랜잭션 없는 호출자에서 소유 검증이 "
                        + "LazyInitializationException 으로 터진다. open-in-view 가 꺼져 있어 "
                        + "요청 범위 컨텍스트가 받아 주지 않는다 (UG-335)")
                .isNotNull();

        assertThat(transactional.readOnly())
                .as("이 클래스는 조회만 한다. 쓰기 가능 트랜잭션이면 더티 체킹 flush 여지가 남는다")
                .isTrue();

        assertThat(transactional.propagation())
                .as("바깥 트랜잭션이 있으면 합류하고 없으면 열어야 한다")
                .isIn((Object[]) 트랜잭션이_열리는_전파);
    }

    /**
     * {@code open-in-view} 가 배포 환경에도 적용되는지 (UG-335).
     *
     * <p>예전에는 {@code local} 프로파일 문서 안에만 있어서 dev·stage·prod·onpremise 가 전부
     * Boot 기본값(true)으로 돌았다. 그 상태에서는 트랜잭션 밖 지연 로딩이 조용히 동작하므로,
     * 위 가드들이 지키려는 선언이 사라져도 아무 일도 일어나지 않는다 — <b>가드가 무력해진다.</b>
     *
     * <p>그래서 설정 파일을 직접 본다. 프로파일 문서 경계는 {@code ---} 이므로, 첫 문서(모든
     * 프로파일 공통)에 이 줄이 있어야 한다.
     */
    @Test
    @DisplayName("open-in-view: false 가 프로파일 공통 문서에 있다")
    void OSIV_가_모든_환경에_적용된다() throws IOException {
        String yaml = Files.readString(Path.of("src/main/resources/application.yaml"));
        String 공통_문서 = yaml.split("(?m)^---\\s*$")[0];

        assertThat(yaml)
                .as("설정 파일을 제대로 읽지 못했다")
                .contains("spring:");
        assertThat(공통_문서)
                .as("open-in-view: false 가 특정 프로파일 문서 안에만 있으면 배포 환경은 "
                        + "Boot 기본값(true)으로 돈다. 그러면 트랜잭션 밖 지연 로딩이 조용히 "
                        + "동작하고, 이 클래스의 다른 가드들도 무력해진다 (UG-335)")
                .contains("open-in-view: false");
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

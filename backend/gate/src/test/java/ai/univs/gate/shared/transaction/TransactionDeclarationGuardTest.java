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
import java.util.List;
import java.util.stream.Stream;
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
     * face 서비스를 Feign 으로 부르므로, 트랜잭션으로 감싸면 원격 호출 내내 영속성 컨텍스트를
     * 붙든 채 네트워크를 기다린다.
     *
     * <p>반박 리뷰가 세 번째 사례({@code CreatePalmFeatureUseCase})를 더 찾았다. 그쪽은
     * 원격 호출이 끝난 뒤 결과를 조립하며 지연 연관을 읽으므로 자기 {@code @Transactional} 로
     * 해결했다 — 쌍둥이인 {@code CreateFaceFeatureUseCase} 와 대칭이 맞다.
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
    @DisplayName("open-in-view: false 가 프로파일 공통 문서에 있고, 어디서도 다시 켜지지 않는다")
    void OSIV_가_모든_환경에_적용된다() throws IOException {
        Path resources = 리소스_루트();
        String yaml = Files.readString(resources.resolve("application.yaml"));
        String 공통_문서 = yaml.split("(?m)^---\\s*$")[0];

        assertThat(yaml)
                .as("설정 파일을 제대로 읽지 못했다")
                .contains("spring:");
        assertThat(공통_문서)
                .as("open-in-view: false 가 특정 프로파일 문서 안에만 있으면 배포 환경은 "
                        + "Boot 기본값(true)으로 돈다. 그러면 트랜잭션 밖 지연 로딩이 조용히 "
                        + "동작하고, 이 클래스의 다른 가드들도 무력해진다 (UG-335)")
                .contains("open-in-view: false");

        // 공통 문서에 있어도 뒤 프로파일 문서나 다른 프로파일 파일이 true 로 덮으면 그만이다
        // (반박 리뷰 지적 — 초판은 '첫 문서에 있는가' 만 봤다).
        List<Path> 설정들;
        try (Stream<Path> files = Files.list(resources)) {
            설정들 = files.filter(f -> f.getFileName().toString().matches("application.*\\.ya?ml"))
                    .sorted().toList();
        }
        assertThat(설정들)
                .as("설정 파일을 한 개도 못 찾았다 — 경로가 어긋났다")
                .isNotEmpty();

        List<String> 되켠_곳 = 설정들.stream()
                .filter(f -> 읽는다(f).matches("(?s).*open-in-view:\\s*true.*"))
                .map(f -> f.getFileName().toString())
                .toList();

        assertThat(되켠_곳)
                .as("어느 프로파일에서든 open-in-view 를 true 로 되돌리면 그 환경만 조용히 "
                        + "예전 동작으로 돌아간다. 껐다는 사실이 무의미해진다")
                .isEmpty();
    }

    /**
     * 리소스 루트. 상대 경로 하나만 쓰면 작업 디렉터리가 다른 곳(IDE, 모노레포 루트)에서
     * 돌릴 때 조용히 엉뚱한 트리를 보거나 예외로 끝난다 (반박 리뷰 지적).
     */
    private static Path 리소스_루트() {
        Path 상대 = Path.of("src/main/resources");
        for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve(상대).resolve("application.yaml"))
                    || Files.isRegularFile(p.resolve(상대).resolve("application.yaml"))) {
                return p.resolve(상대);
            }
            Path viaRoot = p.resolve("backend/gate").resolve(상대);
            if (Files.isRegularFile(viaRoot.resolve("application.yaml"))) {
                return viaRoot;
            }
        }
        throw new IllegalStateException(
                "gate 의 리소스 루트를 찾지 못했다. 작업 디렉터리=" + Path.of("").toAbsolutePath());
    }

    private static String 읽는다(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("설정을 읽을 수 없다: " + file, e);
        }
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

    /**
     * <b>등록·삭제 경로는 메서드 전체에 트랜잭션을 걸지 않는다</b> (UG-336).
     *
     * <p>위의 가드들과 방향이 <b>반대</b>다 — 여기는 선언이 <b>생기는</b> 것을 막는다.
     *
     * <p>메서드 전체가 트랜잭션이면 첫 조회에서 잡은 커넥션을 하위 서비스 원격 호출 내내
     * 붙들고, 그 안에서 {@code HistoryRecorder.start}({@code REQUIRES_NEW})가 두 번째 커넥션을
     * 요구한다. 기본 풀 10에서 동시 요청 10건이 각자 첫 번째를 쥔 채 두 번째를 기다리면 아무도
     * 진행하지 못한다. 매칭 경로는 UG-293, 등록·삭제 경로는 UG-336 에서 뗐다.
     *
     * <p>단위 테스트로는 잡을 수 없다 — Mockito 테스트에는 스프링 프록시가 없어 선언이 있든 없든
     * 똑같이 돈다. "지연 로딩이 터지니 트랜잭션을 걸자" 는 수정이 가장 흔한 되돌림 경로다(UG-335
     * 가 실제로 {@code CreatePalmFeatureUseCase} 에 그렇게 붙였다). 필요한 지연 연관은
     * {@code ApiKeyService} 가 자기 경계 안에서 초기화한다.
     *
     * <p>호출 유스케이스도 함께 본다. 서비스에서 떼도 그것을 부르는 쪽이 감싸면 같은 문제가 된다.
     */
    @Test
    @DisplayName("UG-336: 등록·삭제 경로와 그 호출 유스케이스에는 메서드 전체 트랜잭션이 없다")
    void 등록_삭제_경로는_전체_트랜잭션이_없다() {
        List<Class<?>> 대상 = List.of(
                ai.univs.gate.support.feature.face.FaceFeatureService.class,
                ai.univs.gate.support.feature.palm.PalmFeatureService.class,
                ai.univs.gate.modules.feature.application.usecase.face.DeleteFaceFeatureUseCase.class,
                ai.univs.gate.modules.feature.application.usecase.palm.DeletePalmFeatureUseCase.class,
                ai.univs.gate.modules.feature.application.usecase.face.CreateFaceFeatureUseCase.class,
                ai.univs.gate.modules.feature.application.usecase.face.CreateFaceFeatureByDescriptorUseCase.class,
                ai.univs.gate.modules.feature.application.usecase.palm.CreatePalmFeatureUseCase.class,
                ai.univs.gate.facade.demo.application.usecase.CreateFaceFeatureByApiKeyUseCase.class,
                ai.univs.gate.facade.demo.application.usecase.CreatePalmFeatureByApiKeyUseCase.class);
        List<String> 이름 = List.of("createFaceFeature", "createFaceFeatureByDescriptor",
                "createPalmFeature", "execute");

        List<String> 위반 = new java.util.ArrayList<>();
        List<String> 검사한_메서드 = new java.util.ArrayList<>();
        for (Class<?> c : 대상) {
            for (Method m : c.getDeclaredMethods()) {
                if (!이름.contains(m.getName()) || m.isSynthetic()) {
                    continue;
                }
                검사한_메서드.add(c.getSimpleName() + "." + m.getName());
                if (transactionalOf(m) != null) {
                    위반.add(c.getSimpleName() + "." + m.getName());
                }
            }
        }

        // 개수가 아니라 목록을 단언한다. 이름이 바뀌면 검사에서 조용히 빠지는데, 그러면 이 가드가
        // 공회전한다. 새 진입점을 추가했다면 여기에도 추가할 것.
        assertThat(검사한_메서드).containsExactlyInAnyOrder(
                "FaceFeatureService.createFaceFeature",
                "FaceFeatureService.createFaceFeatureByDescriptor",
                "PalmFeatureService.createPalmFeature",
                "DeleteFaceFeatureUseCase.execute",
                "DeletePalmFeatureUseCase.execute",
                "CreateFaceFeatureUseCase.execute",
                "CreateFaceFeatureByDescriptorUseCase.execute",
                "CreatePalmFeatureUseCase.execute",
                "CreateFaceFeatureByApiKeyUseCase.execute",
                "CreatePalmFeatureByApiKeyUseCase.execute");
        assertThat(위반)
                .as("""
                        메서드 전체 트랜잭션은 원격 호출 내내 커넥션을 쥐고 HistoryRecorder.start 가
                        두 번째 커넥션을 요구하게 만든다 (UG-336). 원자성이 필요한 쓰기는
                        TransactionTemplate 으로 그 쓰기만 감쌀 것. 지연 로딩이 이유라면
                        ApiKeyService 가 초기화해 돌려주는지 먼저 볼 것.""")
                .isEmpty();
    }

    /**
     * <b>그 진입점을 부르는 컨트롤러에도 트랜잭션이 없다</b> (UG-336 반박 리뷰).
     *
     * <p>위 가드는 서비스와 유스케이스만 본다. 컨트롤러에 클래스 레벨 {@code @Transactional} 을
     * 붙이면 그 아래 전부가 바깥 트랜잭션 안에 들어가 UG-336 이 무효가 되는데, 반박 리뷰가 그
     * 변이로 스위트가 초록인 것을 보였다.
     */
    @Test
    @DisplayName("UG-336: 등록·삭제를 부르는 컨트롤러에는 트랜잭션 선언이 없다")
    void 등록_삭제_컨트롤러에는_트랜잭션이_없다() {
        List<Class<?>> 컨트롤러 = List.of(
                ai.univs.gate.modules.feature.api.controller.FaceController.class,
                ai.univs.gate.modules.feature.api.controller.PalmController.class,
                ai.univs.gate.facade.demo.api.controller.DemoController.class);

        List<String> 위반 = new java.util.ArrayList<>();
        for (Class<?> c : 컨트롤러) {
            if (AnnotatedElementUtils.findMergedAnnotation(c, Transactional.class) != null) {
                위반.add(c.getSimpleName() + " (클래스)");
            }
            for (Method m : c.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(m, Transactional.class) != null) {
                    위반.add(c.getSimpleName() + "." + m.getName());
                }
            }
        }

        assertThat(위반)
                .as("컨트롤러의 트랜잭션은 그 아래 등록·삭제 경로 전체를 바깥 트랜잭션으로 감싼다 (UG-336)")
                .isEmpty();
    }
}

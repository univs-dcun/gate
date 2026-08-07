package ai.univs.gate.shared.swagger;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.shared.web.enums.ErrorType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * UG-309: {@code @SwaggerError} 선언이 커밋된 계약 스펙과 일치하는지 본다.
 *
 * <p><b>이 메커니즘을 지키는 검사가 지금까지 하나도 없었다.</b> UG-295 반박 리뷰가 변이 네 개
 * (데모 liveness 의 {@code SETTINGS_NOT_FOUND} 삭제, webhook {@code NOT_OWNERSHIP} 을 403 으로
 * 되돌리기, PalmController 에 {@code NOT_MATCH} 추가, 대시보드 {@code PROJECT_NOT_FOUND} 삭제)를
 * 심었는데 <b>전부 {@code gradlew build} 를 통과했다.</b>
 *
 * <p>CI 도 못 잡는다. 세 겹이다.
 * <ol>
 *   <li>L1b Contract Check 가 {@code BRANCH_NAME == 'dev'} 조건이라 PR 이 아니라 머지 후에 돈다.
 *   <li>{@code Jenkinsfile} 이 {@code contract: 'observe'} 라 깨져도 빌드가 안 죽는다.
 *   <li>enforce 로 바꿔도 못 잡는다. 비교기 {@code openapi-diff} 는 operation·parameter·schema·
 *       응답 코드를 보는데, {@code @SwaggerError} 는 전부
 *       {@code responses.<code>.content.application/json.examples} <b>안</b>에 산다. 네 변이의
 *       응답 코드 집합은 전부 동일했다 — 구조적으로 볼 수 없다.
 * </ol>
 *
 * <p>그래서 {@code api-contract/openapi.json} 은 아무도 실제로 대조하지 않는 기준선이었다.
 *
 * <p><b>왜 앱을 안 띄우는가.</b> {@code generateOpenApiDocs} 로 뽑아 바이트 비교하는 방법도
 * 네 변이를 전부 죽인다. 그러나 그것은 매 {@code clean build} 마다 앱을 잠깐 띄우고(포트 18099)
 * 모든 개발자에게 빌드 시간을 부과한다 — 이 프로젝트는 PR 전 빌드가 필수다. 여기서는
 * {@link SwaggerConfig#settingExamples} 가 만드는 결과를 리플렉션으로 <b>다시 계산</b>해
 * 커밋된 JSON 과 대조한다. 앱도 네트워크도 없다.
 *
 * <p><b>한계.</b> 이 검사는 {@code SwaggerConfig} 의 계산 규칙을 복제한다. 그 규칙이 바뀌면
 * 여기도 함께 바꿔야 한다. 대신 규칙이 바뀌었는데 여기를 안 고치면 <b>즉시 빨개진다</b> —
 * 조용히 어긋나지는 않는다.
 */
@DisplayName("UG-309: @SwaggerError ↔ 계약 스펙 일치")
class SwaggerErrorContractTest {

    private static final String BASE_PACKAGE = "ai.univs.gate";
    private static final File CONTRACT = new File("api-contract/openapi.json");

    /**
     * {@link SwaggerConfig#settingExamples} 가 모든 엔드포인트에 공통으로 심는 항목.
     *
     * <p>여기 이름과 상태가 {@code SwaggerConfig} 와 어긋나면 전 엔드포인트가 한꺼번에
     * 빨개진다 — 그것이 의도다. 공통 항목은 54개 오퍼레이션에 전부 실리므로 오탐일 수 없다.
     *
     * <p><b>이 사본이 {@code SwaggerConfig} 와 따로 논다는 점을 알고 있어야 한다.</b> 반박
     * 리뷰가 {@code SwaggerConfig} 쪽 401 만 다른 {@link ErrorType} 으로 바꾸는 변이를 심었는데
     * 이 테스트는 초록이었다 — 여기는 생성기를 읽지 않고 커밋된 JSON 과 자기 사본만 비교하기
     * 때문이다. 그 방향은 {@code SwaggerConfigCommonExampleTest.공통_슬롯_전부_고정} 이 막는다.
     * 두 테스트가 양쪽 방향을 하나씩 맡는다.
     *
     * <p>403 은 없다. 이 서비스는 403 을 낼 수 없다 — 사유는 {@code SwaggerConfig} 주석 참고.
     */
    private static final Map<String, Integer> 공통 = Map.of(
            ErrorType.UNAUTHORIZED.name(), 401,
            ErrorType.NOT_FOUND.name(), 404,
            ErrorType.METHOD_NOT_ALLOWED.name(), 405,
            ErrorType.INTERNAL_SERVER_ERROR.name(), 500);

    private static JsonNode spec;

    @BeforeAll
    static void 스펙을_읽는다() throws IOException {
        assertThat(CONTRACT)
                .as("계약 기준선이 없다. 경로가 바뀌었다면 이 상수를 고칠 것 (cwd 는 backend/gate)")
                .exists();
        spec = new ObjectMapper().readTree(CONTRACT);
    }

    /**
     * 오퍼레이션마다 기대 집합을 다시 계산해 커밋된 스펙과 맞춘다.
     *
     * <p>어긋남을 처음 하나에서 멈추지 않고 전부 모아서 낸다. {@code SwaggerConfig} 규칙을
     * 바꾸면 54개가 동시에 어긋나는데, 하나씩 고쳐 가며 54번 돌리게 하지 않으려는 것이다.
     */
    @Test
    @DisplayName("선언한 오류 예시가 커밋된 openapi.json 과 정확히 일치한다")
    void 선언과_스펙이_일치한다() {
        List<String> 어긋남 = new ArrayList<>();
        int 검사한_오퍼레이션 = 0;

        for (Handler handler : 핸들러들()) {
            JsonNode operation = spec.path("paths").path(handler.path()).path(handler.method());
            if (operation.isMissingNode()) {
                어긋남.add("%s: 스펙에 %s %s 오퍼레이션이 없다"
                        .formatted(handler.label(), handler.method().toUpperCase(), handler.path()));
                continue;
            }

            검사한_오퍼레이션++;
            Map<Integer, Set<String>> 기대 = 기대_예시(handler.method0());
            Map<Integer, Set<String>> 실제 = 실제_예시(operation);

            if (!기대.equals(실제)) {
                어긋남.add("%s (%s %s)%n      기대: %s%n      실제: %s"
                        .formatted(handler.label(), handler.method().toUpperCase(), handler.path(),
                                기대, 실제));
            }
            어긋남.addAll(본문이_이름과_맞는가(handler, operation));
        }

        assertThat(검사한_오퍼레이션)
                .as("오퍼레이션을 한 개도 못 찾으면 이 가드는 아무것도 검사하지 않는다")
                .isGreaterThan(40);

        assertThat(어긋남)
                .as("@SwaggerError 를 고쳤다면 `sh gradlew generateOpenApiDocs` 후 "
                        + "build/openapi/openapi.json 을 api-contract/openapi.json 으로 복사해 "
                        + "같은 커밋에 넣을 것")
                .isEmpty();
    }

    /**
     * 스펙에는 있는데 코드에서 못 찾은 오퍼레이션이 없는지 반대 방향으로도 본다.
     *
     * <p>위 테스트는 코드 → 스펙 방향이라, 엔드포인트를 지우고 스펙을 안 고치면 통과한다.
     * 그 상태는 없는 API 를 문서가 광고하는 것이라 계약 위반이다.
     */
    @Test
    @DisplayName("스펙에만 있고 코드에는 없는 오퍼레이션이 없다")
    void 스펙에_유령_오퍼레이션이_없다() {
        Set<String> 코드 = new TreeSet<>();
        핸들러들().forEach(h -> 코드.add(h.method() + " " + h.path()));

        Set<String> 유령 = new TreeSet<>();
        spec.path("paths").fields().forEachRemaining(path ->
                path.getValue().fieldNames().forEachRemaining(method -> {
                    String key = method + " " + path.getKey();
                    if (!코드.contains(key)) {
                        유령.add(key);
                    }
                }));

        assertThat(유령)
                .as("엔드포인트를 지우거나 경로를 바꿨다면 계약 기준선도 함께 재생성할 것")
                .isEmpty();
    }

    /**
     * {@link SwaggerConfig#settingExamples} 의 계산을 그대로 재현한다.
     *
     * <p>{@code allErrors.put} 이므로 <b>선언이 공통 항목을 덮어쓴다.</b> 예를 들어
     * {@code @SwaggerError(errorType = NOT_OWNERSHIP, status = 400)} 은 이름이 공통에 없으니
     * 400 버킷에 추가되지만, 공통과 같은 이름을 다른 status 로 선언하면 버킷이 옮겨진다.
     * 그 덮어쓰기 의미를 여기서도 지켜야 실제와 같아진다.
     *
     * <p>{@code @SwaggerErrorExample} 이 아예 없는 핸들러는 {@code settingExamples} 자체가
     * 호출되지 않으므로 예시가 하나도 붙지 않는다 — 빈 맵이 정답이다.
     */
    private static Map<Integer, Set<String>> 기대_예시(Method method) {
        SwaggerErrorExample declared = method.getAnnotation(SwaggerErrorExample.class);
        if (declared == null) {
            return Map.of();
        }

        Map<String, Integer> allErrors = new LinkedHashMap<>(공통);
        for (SwaggerError error : declared.value()) {
            allErrors.put(error.errorType().name(), error.status());
        }

        Map<Integer, Set<String>> byStatus = new TreeMap<>();
        allErrors.forEach((name, status) ->
                byStatus.computeIfAbsent(status, k -> new TreeSet<>()).add(name));
        return byStatus;
    }

    /**
     * 예시 <b>본문</b>의 오류 코드가 그 예시 이름과 맞는지 본다.
     *
     * <p>이름만 보면 기준선의 내용이 썩어도 모른다. 반박 리뷰가 커밋된 JSON 의
     * {@code "PJ-105"} 41곳을 {@code "PJ-999-ROTTEN"} 으로 바꿨는데 전 테스트가 초록이었다 —
     * 그리고 그것은 이 티켓이 고친 403 버그와 <b>정확히 같은 모양</b>의 부패다.
     *
     * <p>{@code ErrorType} 이름으로 코드를 되찾아 대조하므로, {@code ErrorType} 의 코드를
     * 바꾸고 기준선을 재생성하지 않아도 걸린다.
     */
    private static List<String> 본문이_이름과_맞는가(Handler handler, JsonNode operation) {
        List<String> 어긋남 = new ArrayList<>();
        operation.path("responses").fields().forEachRemaining(response -> {
            JsonNode examples = response.getValue()
                    .path("content").path("application/json").path("examples");
            examples.fields().forEachRemaining(example -> {
                String name = example.getKey();
                String code = example.getValue().path("value").path("errors").path("code").asText();
                ErrorType errorType;
                try {
                    errorType = ErrorType.valueOf(name);
                } catch (IllegalArgumentException e) {
                    어긋남.add("%s: 예시 이름 '%s' 에 해당하는 ErrorType 이 없다".formatted(handler.label(), name));
                    return;
                }
                if (!errorType.getCode().equals(code)) {
                    어긋남.add("%s: %s 예시 본문의 코드가 '%s' 인데 ErrorType 은 '%s' 다"
                            .formatted(handler.label(), name, code, errorType.getCode()));
                }
            });
        });
        return 어긋남;
    }

    private static Map<Integer, Set<String>> 실제_예시(JsonNode operation) {
        Map<Integer, Set<String>> byStatus = new TreeMap<>();
        operation.path("responses").fields().forEachRemaining(entry -> {
            JsonNode examples = entry.getValue()
                    .path("content").path("application/json").path("examples");
            if (!examples.isObject() || examples.isEmpty()) {
                return;
            }
            Set<String> names = new TreeSet<>();
            examples.fieldNames().forEachRemaining(names::add);
            byStatus.put(Integer.parseInt(entry.getKey()), names);
        });
        return byStatus;
    }

    // ---------------------------------------------------------------- 스캔

    /** 경로·HTTP 메서드·리플렉션 메서드를 묶은 것. {@code method0} 은 기대 집합 계산에 쓴다. */
    private record Handler(String path, String method, Method method0, String label) {}

    private static List<Handler> 핸들러들() {
        List<Handler> handlers = new ArrayList<>();

        for (Class<?> controller : 컨트롤러들()) {
            if (숨김(controller)) {
                continue;
            }
            List<String> prefixes = 경로들(AnnotatedElementUtils
                    .findMergedAnnotation(controller, RequestMapping.class));

            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping =
                        AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null || 숨김(method)) {
                    continue;
                }
                for (String prefix : prefixes) {
                    for (String suffix : 경로들(mapping)) {
                        for (RequestMethod verb : mapping.method()) {
                            handlers.add(new Handler(
                                    이어붙인다(prefix, suffix),
                                    verb.name().toLowerCase(),
                                    method,
                                    controller.getSimpleName() + "." + method.getName()));
                        }
                    }
                }
            }
        }

        handlers.sort(Comparator.comparing(Handler::path).thenComparing(Handler::method));
        return handlers;
    }

    /**
     * {@code @Hidden} 은 springdoc 이 스펙에서 통째로 빼므로 검사 대상이 아니다.
     *
     * <p>{@code FileController}(클래스 전체)와 {@code DemoController} 의 팜 목록이 여기 해당한다.
     */
    private static boolean 숨김(Object element) {
        return element instanceof Class<?> c
                ? c.isAnnotationPresent(Hidden.class)
                : ((Method) element).isAnnotationPresent(Hidden.class);
    }

    /** 경로가 없는 {@code @RequestMapping} 도 있다 — 그때는 빈 문자열 하나로 친다. */
    private static List<String> 경로들(Annotation annotation) {
        if (annotation == null) {
            return List.of("");
        }
        RequestMapping mapping = (RequestMapping) annotation;
        String[] paths = mapping.path().length > 0 ? mapping.path() : mapping.value();
        return paths.length > 0 ? List.of(paths) : List.of("");
    }

    private static String 이어붙인다(String prefix, String suffix) {
        String joined = (prefix + suffix).replaceAll("/{2,}", "/");
        if (joined.length() > 1 && joined.endsWith("/")) {
            joined = joined.substring(0, joined.length() - 1);
        }
        return joined.isEmpty() ? "/" : joined;
    }

    private static Set<Class<?>> 컨트롤러들() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<Class<?>> found = new LinkedHashSet<>();
        for (BeanDefinition bd : scanner.findCandidateComponents(BASE_PACKAGE)) {
            try {
                Class<?> candidate = Class.forName(bd.getBeanClassName());
                if (프로덕션_클래스(candidate)) {
                    found.add(candidate);
                }
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(bd.getBeanClassName(), e);
            }
        }
        return found;
    }

    /**
     * 테스트 소스에 있는 {@code @RestController} 를 걸러낸다.
     *
     * <p>스캐너는 클래스패스를 보므로 테스트 픽스처도 함께 걸린다 — 실제로
     * {@code PageConditionBindingTest} 의 {@code ProbeController} 가 잡혔다. 그것은 스펙에
     * 실릴 이유가 없는데도 "스펙에 없다" 로 오탐이 난다.
     *
     * <p>{@code classes/java/main} 아래에서 온 것만 남긴다. 빌드 레이아웃이 바뀌어 이 판정이
     * 전부를 걸러 버리면 {@code 검사한_오퍼레이션 > 40} 단언이 잡는다.
     */
    private static boolean 프로덕션_클래스(Class<?> type) {
        var source = type.getProtectionDomain().getCodeSource();
        return source != null && source.getLocation().getPath().contains("classes/java/main");
    }
}

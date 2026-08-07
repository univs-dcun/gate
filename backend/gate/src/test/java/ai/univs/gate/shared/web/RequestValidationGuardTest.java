package ai.univs.gate.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Constraint;
import jakarta.validation.Valid;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * UG-310: 제약이 달린 요청 DTO 에는 반드시 {@code @Valid} 가 붙어 있어야 한다.
 *
 * <p>{@code DemoController.getProjectConfigByApiKey} 가 {@code @Valid} 없이
 * {@code @RequestBody DemoProjectConfigRequestDTO} 를 받고 있었다. 그 DTO 의
 * {@code @NotBlank}·{@code @Length(max = 36)} 는 <b>한 번도 실행되지 않았다.</b> 빈 문자열이
 * 그대로 조회까지 내려가 {@code API_KEY_NOT_FOUND} 로 끝났고, 36자를 넘는 값도 통과했다.
 *
 * <p>이런 누락은 눈으로 안 보인다 — 어노테이션이 <b>있는 것</b>이 아니라 <b>없는 것</b>이라
 * 코드 리뷰에서 시선이 머물 자리가 없고, 검증이 꺼져도 엔드포인트는 정상 동작하는 것처럼
 * 보인다. openapi-diff 도 못 잡는다: {@code @Valid} 는 스펙에 아무 흔적을 남기지 않는다.
 *
 * <p>그래서 한 자리에서 전수로 본다. 앱을 띄우지 않는 순수 리플렉션 검사라 비용이 없다.
 */
@DisplayName("UG-310: 요청 DTO 검증 어노테이션 가드")
class RequestValidationGuardTest {

    private static final String BASE_PACKAGE = "ai.univs.gate";

    /**
     * 제약이 달린 DTO 를 받으면서 {@code @Valid} 가 없는 컨트롤러 파라미터를 전부 모은다.
     *
     * <p>{@code @RequestBody} 와 {@code @ModelAttribute} 둘 다 본다. 후자는 쿼리 파라미터
     * 바인딩이라 놓치기 더 쉽다 — 대시보드에서 {@code /daily} 만 {@code @Valid} 가 있고
     * 나머지 셋은 없는데, 그 셋의 DTO 에는 제약이 없어서 문제가 아니다. 제약이 생기는 순간
     * 이 테스트가 알려 준다.
     */
    @Test
    @DisplayName("제약이 있는 요청 DTO 는 전부 @Valid 를 달고 있다")
    void 제약이_있으면_Valid_가_붙어_있다() {
        List<String> 누락 = new ArrayList<>();

        for (Class<?> controller : 컨트롤러들()) {
            for (Method method : controller.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (!바인딩_대상(parameter) || parameter.isAnnotationPresent(Valid.class)) {
                        continue;
                    }
                    if (제약이_있다(parameter.getType(), new LinkedHashSet<>())) {
                        누락.add("%s.%s(%s %s)".formatted(
                                controller.getSimpleName(), method.getName(),
                                parameter.getType().getSimpleName(), parameter.getName()));
                    }
                }
            }
        }

        assertThat(누락)
                .as("이 DTO 들의 @NotBlank·@Length 같은 제약은 @Valid 가 없으면 한 줄도 돌지 않는다. "
                        + "엔드포인트는 정상으로 보이지만 입력 검증만 조용히 꺼져 있다")
                .isEmpty();
    }

    /**
     * 가드가 실제로 무언가를 검사하고 있음을 보장한다.
     *
     * <p>패키지명 오타나 스캐너 설정 변경으로 대상이 0개가 되면 위 테스트는 영원히 초록이다.
     * 그 상태가 "위반 없음" 과 구분되지 않는다.
     */
    @Test
    @DisplayName("검사 대상 컨트롤러와 제약 DTO 를 실제로 찾아낸다")
    void 가드가_공회전하지_않는다() {
        Set<Class<?>> controllers = 컨트롤러들();
        assertThat(controllers).as("컨트롤러를 한 개도 못 찾으면 이 가드는 아무것도 검사하지 않는다")
                .hasSizeGreaterThan(5);

        long 제약_파라미터 = controllers.stream()
                .flatMap(c -> java.util.Arrays.stream(c.getDeclaredMethods()))
                .flatMap(m -> java.util.Arrays.stream(m.getParameters()))
                .filter(RequestValidationGuardTest::바인딩_대상)
                .filter(p -> 제약이_있다(p.getType(), new LinkedHashSet<>()))
                .count();

        assertThat(제약_파라미터)
                .as("제약이 달린 요청 DTO 를 한 개도 못 찾으면 제약이_있다() 판정이 망가진 것이다")
                .isGreaterThan(0);
    }

    private static Set<Class<?>> 컨트롤러들() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<Class<?>> found = new LinkedHashSet<>();
        for (BeanDefinition bd : scanner.findCandidateComponents(BASE_PACKAGE)) {
            try {
                found.add(Class.forName(bd.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(bd.getBeanClassName(), e);
            }
        }
        return found;
    }

    private static boolean 바인딩_대상(Parameter parameter) {
        return parameter.isAnnotationPresent(RequestBody.class)
                || parameter.isAnnotationPresent(ModelAttribute.class);
    }

    /**
     * 타입 어딘가에 Bean Validation 제약이 하나라도 있는가.
     *
     * <p>중첩 record 까지 따라 들어간다 — 바깥에 제약이 없어도 안쪽에 있으면 {@code @Valid} 가
     * 필요하다. {@code 방문} 집합으로 자기 참조 구조에서 무한 재귀를 막는다.
     */
    private static boolean 제약이_있다(Class<?> type, Set<Class<?>> 방문) {
        if (type == null || !type.getName().startsWith(BASE_PACKAGE) || !방문.add(type)) {
            return false;
        }

        for (RecordComponent component : 레코드_구성요소(type)) {
            if (제약_어노테이션이_있다(component) || 제약이_있다(component.getType(), 방문)) {
                return true;
            }
        }

        for (var field : type.getDeclaredFields()) {
            if (제약_어노테이션이_있다(field) || 제약이_있다(field.getType(), 방문)) {
                return true;
            }
        }
        return false;
    }

    private static RecordComponent[] 레코드_구성요소(Class<?> type) {
        return type.isRecord() ? type.getRecordComponents() : new RecordComponent[0];
    }

    /**
     * {@code @Constraint} 메타 어노테이션으로 판정한다. {@code @NotBlank} 를 이름으로 열거하면
     * 이 프로젝트의 {@code @ValidImageFile} 같은 커스텀 제약을 놓친다.
     */
    private static boolean 제약_어노테이션이_있다(AnnotatedElement element) {
        for (Annotation annotation : element.getAnnotations()) {
            if (annotation.annotationType().isAnnotationPresent(Constraint.class)) {
                return true;
            }
        }
        return false;
    }
}

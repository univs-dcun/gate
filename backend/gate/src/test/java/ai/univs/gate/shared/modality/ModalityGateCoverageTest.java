package ai.univs.gate.shared.modality;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.server.PathContainer;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * 차단 규칙이 <b>실제 엔드포인트</b>를 빠짐없이 덮는가 (UG-223).
 *
 * <p>{@code ModalityGateInterceptor} 는 경로와 메서드만 본다 — modality 경로 아래에서 {@code GET}
 * 이 아닌 요청을 막는다. 그 규칙이 옳으려면 두 가지가 참이어야 한다.
 *
 * <ol>
 *   <li>face·palm 의 동작 엔드포인트가 전부 그 경로 아래에 있다. 다른 곳에 palm 동작을 하나 만들면
 *       차단을 우회해 생체 이미지와 실패 이력이 다시 쌓인다.
 *   <li>한 방식의 패턴이 다른 방식의 엔드포인트를 먹지 않는다. palm 을 끄면 face 가 막히는 식이면
 *       face 전용 납품이 멈춘다.
 * </ol>
 *
 * <p>인터셉터 단위 테스트로는 둘 다 잡을 수 없다 — 경로 문자열을 테스트가 직접 넣기 때문이다.
 * 여기서는 {@code @RestController} 를 전부 스캔해 실제 매핑을 모은다.
 */
@DisplayName("UG-223: 차단 규칙의 엔드포인트 커버리지")
class ModalityGateCoverageTest {

    private record Mapping(String controller, String path, List<RequestMethod> methods) {

        boolean 조회뿐() {
            // 메서드를 지정하지 않은 매핑은 모든 메서드를 받는다 — 동작으로 본다.
            return !methods.isEmpty() && methods.stream().allMatch(m -> m == RequestMethod.GET || m == RequestMethod.HEAD);
        }

        boolean 경로에_있다(String 방식) {
            // 세그먼트 단위로 본다. "/palms" 같은 목록 경로도 palm 의 것이다.
            return Arrays.stream(path.toLowerCase(Locale.ROOT).split("/"))
                    .anyMatch(seg -> seg.equals(방식) || seg.equals(방식 + "s"));
        }

        @Override
        public String toString() {
            return controller + " " + methods + " " + path;
        }
    }

    private static final List<Mapping> 전체 = new ArrayList<>();

    @BeforeAll
    static void 컨트롤러를_스캔한다() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        for (var bd : scanner.findCandidateComponents("ai.univs.gate")) {
            Class<?> c = Class.forName(bd.getBeanClassName());
            RequestMapping onClass = AnnotatedElementUtils.findMergedAnnotation(c, RequestMapping.class);
            String[] prefixes = onClass == null || onClass.path().length == 0 ? new String[]{""} : onClass.path();

            for (Method m : c.getDeclaredMethods()) {
                RequestMapping rm = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                if (rm == null) {
                    continue;
                }
                String[] paths = rm.path().length == 0 ? new String[]{""} : rm.path();
                for (String prefix : prefixes) {
                    for (String p : paths) {
                        전체.add(new Mapping(c.getSimpleName(), prefix + p, List.of(rm.method())));
                    }
                }
            }
        }
    }

    private static boolean 막히는가(FeatureType 방식, String path) {
        PathContainer container = PathContainer.parsePath(path);
        return ModalityGateInterceptor.pathsOf(방식).stream()
                .map(PathPatternParser.defaultInstance::parse)
                .anyMatch(pattern -> pattern.matches(container));
    }

    /** 스캔이 공회전하면 아래 테스트는 전부 초록이 된다. */
    @Test
    @DisplayName("전제: face·palm 동작 엔드포인트를 실제로 찾았다")
    void 스캔이_공회전하지_않는다() {
        assertThat(전체).extracting(Mapping::controller)
                .contains("FaceController", "PalmController", "DemoController");
        assertThat(전체).filteredOn(m -> !m.조회뿐() && m.경로에_있다("palm")).hasSizeGreaterThanOrEqualTo(7);
        assertThat(전체).filteredOn(m -> !m.조회뿐() && m.경로에_있다("face")).hasSizeGreaterThanOrEqualTo(15);
    }

    @Test
    @DisplayName("palm 이 들어간 동작 엔드포인트는 어디에 있든 palm 차단 경로 안에 있다")
    void palm_동작은_전부_덮인다() {
        List<Mapping> 새는_것 = 전체.stream()
                .filter(m -> !m.조회뿐() && m.경로에_있다("palm"))
                .filter(m -> !막히는가(FeatureType.PALM, m.path()))
                .toList();

        assertThat(새는_것)
                .as("palm 동작이 차단 경로 밖에 있다 — 끈 배포에서도 이미지·실패 이력이 쌓인다. "
                        + "경로를 /api/v1/feature/palm/** 아래로 옮기거나 ModalityGateInterceptor 의 패턴을 넓힐 것")
                .isEmpty();
    }

    @Test
    @DisplayName("face 가 들어간 동작 엔드포인트는 어디에 있든 face 차단 경로 안에 있다")
    void face_동작은_전부_덮인다() {
        List<Mapping> 새는_것 = 전체.stream()
                .filter(m -> !m.조회뿐() && m.경로에_있다("face"))
                .filter(m -> !막히는가(FeatureType.FACE, m.path()))
                .toList();

        assertThat(새는_것).isEmpty();
    }

    /** palm 을 끄면 face 가 막히는 식이면 face 전용 납품이 멈춘다. 반대도 같다. */
    @Test
    @DisplayName("한 방식의 차단 경로가 다른 방식의 엔드포인트를 먹지 않는다")
    void 서로를_막지_않는다() {
        assertThat(전체).filteredOn(m -> m.경로에_있다("face") && 막히는가(FeatureType.PALM, m.path())).isEmpty();
        assertThat(전체).filteredOn(m -> m.경로에_있다("palm") && 막히는가(FeatureType.FACE, m.path())).isEmpty();
    }

    /**
     * face·palm 이 아닌 엔드포인트는 어느 차단에도 걸리지 않는다.
     *
     * <p>예를 들어 {@code /api/v1/feature} 통합 목록이나 {@code /api/v1/match} 이력이 palm 차단에
     * 걸리면, palm 을 끈 배포에서 face 의 화면까지 깨진다.
     */
    @Test
    @DisplayName("face·palm 이 아닌 엔드포인트는 어느 차단 경로에도 없다")
    void 나머지는_건드리지_않는다() {
        assertThat(전체)
                .filteredOn(m -> !m.경로에_있다("face") && !m.경로에_있다("palm"))
                .filteredOn(m -> 막히는가(FeatureType.FACE, m.path()) || 막히는가(FeatureType.PALM, m.path()))
                .isEmpty();
    }
}

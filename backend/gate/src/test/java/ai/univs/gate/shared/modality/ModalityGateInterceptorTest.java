package ai.univs.gate.shared.modality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 꺼 둔 방식의 동작만 막고 조회는 통과시키는가 (UG-223).
 *
 * <p>조회까지 막으면 온프레미스 gate-web 의 palm 화면(탭·차트·로그)이 빈 값 대신 오류를 보인다.
 * 온프레미스는 빈 값을 보이는 것으로 고객 안내를 확정했다.
 */
@DisplayName("UG-223: 생체 인증 방식 차단 인터셉터")
class ModalityGateInterceptorTest {

    private static final ModalityProperties 팜_꺼짐 = new ModalityProperties(true, false);
    private static final ModalityProperties 둘_다_켜짐 = new ModalityProperties(true, true);

    private static boolean 통과하는가(ModalityGateInterceptor interceptor, String method) {
        return interceptor.preHandle(
                new MockHttpServletRequest(method, "/api/v1/feature/palm"), new MockHttpServletResponse(), new Object());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"POST", "DELETE", "PUT", "PATCH"})
    @DisplayName("꺼져 있으면 동작 메서드는 FEATURE_NOT_ENABLED 로 거절한다")
    void 꺼지면_동작을_거절한다(String method) {
        var interceptor = new ModalityGateInterceptor(FeatureType.PALM, 팜_꺼짐);

        assertThatThrownBy(() -> 통과하는가(interceptor, method))
                .isInstanceOf(CustomGateException.class)
                .extracting(e -> ((CustomGateException) e).getErrorType())
                .isEqualTo(ErrorType.FEATURE_NOT_ENABLED);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
    @DisplayName("꺼져 있어도 조회 메서드는 통과시킨다 — palm 화면은 빈 값을 보여야 한다")
    void 꺼져도_조회는_통과한다(String method) {
        assertThat(통과하는가(new ModalityGateInterceptor(FeatureType.PALM, 팜_꺼짐), method)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"POST", "DELETE", "GET"})
    @DisplayName("켜져 있으면 무엇이든 통과시킨다 — 이 기능 전과 같은 동작")
    void 켜지면_전부_통과한다(String method) {
        assertThat(통과하는가(new ModalityGateInterceptor(FeatureType.PALM, 둘_다_켜짐), method)).isTrue();
    }

    /** palm 을 껐다고 face 가 막히면 face 전용 납품이 전부 멈춘다. */
    @Test
    @DisplayName("한쪽을 꺼도 다른 쪽은 막지 않는다")
    void 다른_방식은_영향이_없다() {
        assertThat(통과하는가(new ModalityGateInterceptor(FeatureType.FACE, 팜_꺼짐), "POST")).isTrue();
    }

    private static ModalityProperties 바인딩(org.springframework.core.env.PropertySource<?> source) {
        return new org.springframework.boot.context.properties.bind.Binder(
                        org.springframework.boot.context.properties.source.ConfigurationPropertySources.from(source))
                .bindOrCreate("gate.features", ModalityProperties.class);
    }

    @Test
    @DisplayName("아무 설정이 없으면 둘 다 켜짐이다 — 클라우드 배포의 동작이 바뀌지 않는다")
    void 기본값은_둘_다_켜짐() {
        var props = 바인딩(new org.springframework.core.env.MapPropertySource("empty", java.util.Map.of()));

        assertThat(props.face()).isTrue();
        assertThat(props.palm()).isTrue();
    }

    /**
     * <b>온프레미스 계약의 이름 그대로</b> 붙는지 (UG-223).
     *
     * <p>온프레미스는 compose 환경변수 {@code GATE_FEATURES_PALM=false} 로 끈다. 스프링의 느슨한
     * 바인딩이 그 이름을 {@code gate.features.palm} 으로 읽어야 한다 — 이름이 조금만 달라도
     * (예: {@code GATE_FEATURE_PALM}) 조용히 무시되고 palm 이 켜진 채로 납품된다.
     */
    @Test
    @DisplayName("환경변수 GATE_FEATURES_PALM=false 로 끌 수 있다 — 온프레미스 계약")
    void 환경변수로_끈다() {
        var props = 바인딩(new org.springframework.core.env.SystemEnvironmentPropertySource(
                "env", java.util.Map.of("GATE_FEATURES_PALM", "false")));

        assertThat(props.palm()).as("GATE_FEATURES_PALM").isFalse();
        assertThat(props.face()).as("주지 않은 쪽은 기본값").isTrue();
    }

    @Test
    @DisplayName("환경변수 GATE_FEATURES_FACE=false 로 끌 수 있다")
    void 환경변수로_face_를_끈다() {
        var props = 바인딩(new org.springframework.core.env.SystemEnvironmentPropertySource(
                "env", java.util.Map.of("GATE_FEATURES_FACE", "false")));

        assertThat(props.face()).isFalse();
        assertThat(props.palm()).isTrue();
    }
}

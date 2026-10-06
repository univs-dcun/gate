package ai.univs.face.shared.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import feign.RequestTemplate;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@DisplayName("UG-352: 하위 서비스에는 이 서비스가 정한 언어를 넘긴다")
class FeignLocaleForwardingTest {

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
        LocaleContextHolder.resetLocaleContext();
    }

    private RequestTemplate 보낸다(String header, Locale resolved) {
        var request = new MockHttpServletRequest();
        if (header != null) request.addHeader("Accept-Language", header);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        LocaleContextHolder.setLocale(resolved);
        var template = new RequestTemplate();
        new CommonFeignConfig(mock(Tracer.class), mock(Propagator.class)).requestInterceptor().apply(template);
        return template;
    }

    @Test
    @DisplayName("원본 헤더('*', 'ko,en;q=0.9')가 아니라 해석한 언어 — 한 응답 안에서 언어가 섞이지 않게")
    void 해석한_언어() {
        assertThat(보낸다("*", Locale.ENGLISH).headers().get("Accept-Language")).containsExactly("en");
        assertThat(보낸다("ko,en;q=0.9", Locale.KOREAN).headers().get("Accept-Language")).containsExactly("ko");
    }

    @Test
    @DisplayName("헤더가 없어도 이 서비스의 언어를 넘긴다 — 하위 서비스 기본값으로 갈리지 않게")
    void 헤더_없음() {
        assertThat(보낸다(null, Locale.ENGLISH).headers().get("Accept-Language")).containsExactly("en");
    }
}

package ai.univs.gate.support.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.codec.ErrorDecoder;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
@RequiredArgsConstructor
public class CommonFeignConfig {

    private final Tracer tracer;
    private final Propagator propagator;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            Span span = tracer.currentSpan();
            if (span != null) {
                propagator.inject(
                        span.context(),
                        requestTemplate,
                        (RequestTemplate template, String headerName, String headerValue) -> template.header(headerName, headerValue)
                );
            }

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                // UG-352: 원본 헤더가 아니라 이 서비스가 정한 언어를 넘긴다. 원본을 넘기면 헤더가 없거나 '*' 일 때 gate 문구는
                // gate 기본(영어), 하위 서비스 문구는 그쪽 기본(한국어)이 되어 한 응답 안에서 언어가 섞인다 (반박 리뷰 W1).
                requestTemplate.header("Accept-Language", LocaleContextHolder.getLocale().toLanguageTag());

                String acceptTimezone = request.getHeader("Accept-TimeZone");
                if (acceptTimezone != null) requestTemplate.header("Accept-TimeZone", acceptTimezone);
            }
        };
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new CommonErrorDecoder();
    }
}

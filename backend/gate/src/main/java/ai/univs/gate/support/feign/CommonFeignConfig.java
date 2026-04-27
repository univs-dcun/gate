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

                String acceptLanguage = request.getHeader("Accept-Language");
                if (acceptLanguage != null) requestTemplate.header("Accept-Language", acceptLanguage);

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

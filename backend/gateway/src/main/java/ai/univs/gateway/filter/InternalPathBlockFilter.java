package ai.univs.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 서비스 간 내부 전용 경로(/api/{version}/{service}/internal/** 등)가 게이트웨이를 통해
 * 외부에 노출되는 것을 차단한다. 라우트 설정과 무관하게 전역 적용되는 방어선.
 */
@Slf4j
@Component
public class InternalPathBlockFilter implements GlobalFilter, Ordered {

    private static final String INTERNAL_PATH_PATTERN = "/api/**/internal/**";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 세그먼트별 matrix variable(;a=b)을 제거해 /internal;x=1/ 형태의 우회를 차단
        String path = exchange.getRequest().getPath().pathWithinApplication().value()
                .replaceAll(";[^/]*", "");
        if (pathMatcher.match(INTERNAL_PATH_PATTERN, path)) {
            log.warn("Blocked external access to internal path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

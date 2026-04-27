package ai.univs.gateway.filter;

import ai.univs.gateway.client.AuthClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.AuthenticationConfig> {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthClient authClient;

    public AuthenticationFilter(AuthClient authClient) {
        super(AuthenticationConfig.class);
        this.authClient = authClient;
    }

    @Override
    public GatewayFilter apply(AuthenticationConfig config) {
        return (exchange, chain) -> {
            HttpHeaders headers = exchange.getRequest().getHeaders();

            // JWT
            String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
            if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
                log.warn("Missing or malformed Authorization header: path={}", exchange.getRequest().getURI());
                return exchange.getResponse().setComplete();
            }

            String lang = headers.getFirst(HttpHeaders.ACCEPT_LANGUAGE);
            String accessToken = authorization.substring(BEARER_PREFIX.length());

            // JWT validation
            return authClient.validateToken(accessToken, lang)
                    .flatMap(authResponse -> {
                        if (!authResponse.isValid()) {
                            log.warn("Invalid token: path={}", exchange.getRequest().getURI());
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }

                        log.info("Token validated: accountId={}", authResponse.getAccountId());

                        ServerWebExchange mutatedExchange = exchange.mutate()
                                .request(
                                        exchange.getRequest().mutate()
                                                .header("X-Account-Id", authResponse.getAccountId().toString())
                                                .build()
                                )
                                .build();

                        return chain.filter(mutatedExchange);
                    })
                    .onErrorResume(error -> {
                        log.error("Authentication failed: path={}, error={}", exchange.getRequest().getURI(), error.getMessage());

                        if (error instanceof WebClientResponseException ex) {
                            exchange.getResponse().setStatusCode(ex.getStatusCode());
                            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                            byte[] bytes = ex.getResponseBodyAsByteArray();
                            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                            return exchange.getResponse().writeWith(Mono.just(buffer));
                        }

                        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                        return exchange.getResponse().setComplete();
                    });
        };
    }

    public static class AuthenticationConfig { }
}

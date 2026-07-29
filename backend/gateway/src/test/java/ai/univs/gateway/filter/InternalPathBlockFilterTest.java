package ai.univs.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("InternalPathBlockFilter 단위 테스트")
class InternalPathBlockFilterTest {

    private final InternalPathBlockFilter filter = new InternalPathBlockFilter();

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/company/internal/init",
            "/api/v1/company/internal/init/sub",
            "/api/v2/other-service/internal/anything",
            "/api/v1/company/internal;x=1/init",
            "/api/v1;v=2/company/internal/init",
    })
    @DisplayName("internal 경로는 downstream으로 전달하지 않고 404를 반환한다")
    void internalPath_blockedWith404(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(chain, never()).filter(exchange);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/company",
            "/api/v1/projects/1",
            "/api/v1/auth/token/validate",
            "/internal/health",
    })
    @DisplayName("internal 패턴이 아닌 경로는 그대로 통과시킨다")
    void normalPath_passesThrough(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        given(chain.filter(exchange)).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        verify(chain).filter(exchange);
    }
}

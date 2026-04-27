package ai.univs.gateway.filter;

import ai.univs.gateway.client.AuthClient;
import ai.univs.gateway.client.AuthResponseDTO;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AuthenticationFilterTest {

    // Spring 컨텍스트 생성 전에 downstream 서버를 기동해야 하므로 static 초기화
    static final MockWebServer downstreamServer;

    static {
        try {
            downstreamServer = new MockWebServer();
            downstreamServer.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void registerDownstreamUrl(DynamicPropertyRegistry registry) {
        registry.add("test.downstream-url", () -> downstreamServer.url("/").toString());
    }

    @AfterAll
    static void tearDown() throws IOException {
        downstreamServer.shutdown();
    }

    @MockitoBean
    private AuthClient authClient;

    @Autowired
    private WebTestClient webTestClient;

    // AuthenticationFilter가 적용된 테스트 전용 라우트 정의
    @TestConfiguration
    static class TestRouteConfig {

        @Value("${test.downstream-url}")
        private String downstreamUrl;

        @Bean
        RouteLocator testRoutes(RouteLocatorBuilder builder, AuthenticationFilter authFilter) {
            return builder.routes()
                    .route("test-route", r -> r.path("/test/**")
                            .filters(f -> f.filter(
                                    authFilter.apply(new AuthenticationFilter.AuthenticationConfig())))
                            .uri(downstreamUrl))
                    .build();
        }
    }

    // -------------------------------------------------------------------------
    // 정상 케이스
    // -------------------------------------------------------------------------

    @Test
    void 유효한_토큰이면_X_Account_Id를_세팅하여_downstream에_전달한다() throws InterruptedException {
        given(authClient.validateToken(anyString(), any()))
                .willReturn(Mono.just(new AuthResponseDTO(true, 42L)));

        downstreamServer.enqueue(new MockResponse().setResponseCode(200));

        webTestClient.get()
                .uri("/test/hello")
                .header("Authorization", "Bearer valid-token")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest downstream = downstreamServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(downstream).isNotNull();
        assertThat(downstream.getHeader("X-Account-Id")).isEqualTo("42");
    }

    @Test
    void AcceptLanguage_헤더를_auth_서비스에_전달한다() {
        given(authClient.validateToken("valid-token", "ko-KR"))
                .willReturn(Mono.just(new AuthResponseDTO(true, 1L)));

        downstreamServer.enqueue(new MockResponse().setResponseCode(200));

        webTestClient.get()
                .uri("/test/hello")
                .header("Authorization", "Bearer valid-token")
                .header("Accept-Language", "ko-KR")
                .exchange()
                .expectStatus().isOk();

        verify(authClient).validateToken("valid-token", "ko-KR");
    }

    // -------------------------------------------------------------------------
    // 인증 실패 케이스
    // -------------------------------------------------------------------------

    @Test
    void 유효하지_않은_토큰이면_401을_반환한다() {
        given(authClient.validateToken(anyString(), any()))
                .willReturn(Mono.just(new AuthResponseDTO(false, null)));

        webTestClient.get()
                .uri("/test/hello")
                .header("Authorization", "Bearer invalid-token")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void Authorization_헤더가_없으면_빈_응답을_반환한다() {
        webTestClient.get()
                .uri("/test/hello")
                .exchange()
                .expectStatus().isOk()
                .expectBody().isEmpty();
    }

    @Test
    void Bearer가_아닌_Authorization_헤더이면_빈_응답을_반환한다() {
        webTestClient.get()
                .uri("/test/hello")
                .header("Authorization", "Basic dXNlcjpwYXNz")
                .exchange()
                .expectStatus().isOk()
                .expectBody().isEmpty();
    }

    // -------------------------------------------------------------------------
    // auth-service 오류 케이스
    // -------------------------------------------------------------------------

    @Test
    void auth_서비스_장애시_503을_반환한다() {
        given(authClient.validateToken(anyString(), any()))
                .willReturn(Mono.error(new RuntimeException("auth-service unavailable")));

        webTestClient.get()
                .uri("/test/hello")
                .header("Authorization", "Bearer some-token")
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void auth_서비스가_WebClientResponseException을_던지면_해당_상태코드와_바디를_그대로_반환한다() {
        org.springframework.web.reactive.function.client.WebClientResponseException authError =
                org.springframework.web.reactive.function.client.WebClientResponseException.create(
                        401,
                        "Unauthorized",
                        org.springframework.http.HttpHeaders.EMPTY,
                        """
                        {"success":false,"data":null,"errors":[{"code":"EXPIRATION_TOKEN"}]}
                        """.getBytes(),
                        null
                );

        given(authClient.validateToken(anyString(), any()))
                .willReturn(Mono.error(authError));

        webTestClient.get()
                .uri("/test/hello")
                .header("Authorization", "Bearer expired-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("EXPIRATION_TOKEN"));
    }
}

package ai.univs.gateway.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class AuthClientTest {

    private MockWebServer mockWebServer;
    private AuthClient authClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        authClient = new AuthClient(WebClient.builder(), mockWebServer.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void 유효한_토큰이면_valid_true와_accountId_email을_반환한다() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"success":true,"data":{"valid":true,"accountId":42,"email":"user@univs.ai"},"errors":null}
                        """));

        StepVerifier.create(authClient.validateToken("valid-token", "ko"))
                .assertNext(dto -> {
                    assertThat(dto.isValid()).isTrue();
                    assertThat(dto.getAccountId()).isEqualTo(42L);
                    assertThat(dto.getEmail()).isEqualTo("user@univs.ai");
                })
                .verifyComplete();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/v1/auth/token/validate");
        assertThat(request.getHeader("Accept-Language")).isEqualTo("ko");
        assertThat(request.getBody().readUtf8()).contains("valid-token");
    }

    @Test
    void 유효하지_않은_토큰이면_valid_false와_null_accountId를_반환한다() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"success":true,"data":{"valid":false,"accountId":null},"errors":null}
                        """));

        StepVerifier.create(authClient.validateToken("invalid-token", null))
                .assertNext(dto -> {
                    assertThat(dto.isValid()).isFalse();
                    assertThat(dto.getAccountId()).isNull();
                    assertThat(dto.getEmail()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void auth_서비스가_4xx를_반환하면_WebClientResponseException을_던진다() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"success":false,"data":null,"errors":[{"code":"INVALID_INPUT"}]}
                        """));

        StepVerifier.create(authClient.validateToken("bad-token", null))
                .expectError(WebClientResponseException.BadRequest.class)
                .verify();
    }

    @Test
    void auth_서비스가_5xx를_반환하면_WebClientResponseException을_던진다() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"success":false,"data":null,"errors":[{"code":"INTERNAL_ERROR"}]}
                        """));

        StepVerifier.create(authClient.validateToken("token", null))
                .expectError(WebClientResponseException.InternalServerError.class)
                .verify();
    }

    @Test
    void language가_null이면_AcceptLanguage_헤더를_보내지_않는다() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"success":true,"data":{"valid":true,"accountId":1},"errors":null}
                        """));

        StepVerifier.create(authClient.validateToken("token", null))
                .assertNext(dto -> assertThat(dto.isValid()).isTrue())
                .verifyComplete();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getHeader("Accept-Language")).isNull();
    }
}

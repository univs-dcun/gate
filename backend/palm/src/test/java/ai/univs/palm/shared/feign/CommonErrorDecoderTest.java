package ai.univs.palm.shared.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.univs.palm.shared.exception.CustomFeignException;
import ai.univs.palm.shared.exception.UpstreamCallException;
import ai.univs.palm.shared.web.enums.ErrorType;
import feign.Client;
import feign.Request;
import feign.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;

/**
 * UG-299: ML 모듈 오류를 어떤 타입으로 바꾸는가.
 *
 * <p>이 클래스에는 테스트가 하나도 없었다. 반박 리뷰가 변이로 그것을 드러냈다 — 3xx/5xx 분기와
 * 파싱 실패 분기의 코드를 통째로 지워도 전 테스트가 초록이었다. 이 디코더는 <b>UG-299 가
 * 고치려던 바로 그 경로</b>다.
 *
 * <p>여기서 무엇을 던지는지가 위쪽 로깅을 결정한다. {@link UpstreamCallException} 이 아니면
 * 상태 코드가 그 자리에서 사라져 502·503·리다이렉트를 구분할 수 없게 된다.
 */
@DisplayName("UG-299: CommonErrorDecoder")
class CommonErrorDecoderTest {

    private final CommonErrorDecoder decoder = new CommonErrorDecoder();

    private static Response response(int status, String body) {
        return Response.builder()
                .status(status)
                .reason("test-reason")
                .request(Request.create(
                        Request.HttpMethod.POST,
                        "http://palm-module/verify",
                        Collections.emptyMap(),
                        new byte[0],
                        StandardCharsets.UTF_8,
                        null))
                .headers(Collections.emptyMap())
                .body(body, StandardCharsets.UTF_8)
                .build();
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 500, 502, 503})
    @DisplayName("3xx·5xx 는 상태 코드를 실은 UpstreamCallException 이 된다")
    void 하위_오류는_상태코드를_싣는다(int status) {
        Exception result = decoder.decode("PalmClient#verify()", response(status, ""));

        assertThat(result).isInstanceOf(UpstreamCallException.class);

        UpstreamCallException ex = (UpstreamCallException) result;
        assertThat(ex.getUpstreamStatus())
                .as("이 값이 사라지면 위쪽에서 502 와 503 을 구분할 수 없다")
                .isEqualTo(status);
        assertThat(ex.getOperation()).isEqualTo("PalmClient#verify()");
        assertThat(ex.getReason()).isEqualTo("test-reason");
        assertThat(ex.getCause())
                .as("하위가 오류를 응답한 경우는 우리 호출 스택에 정보가 없다 — 스택트레이스를 남기지 않는다")
                .isNull();
    }

    @Test
    @DisplayName("본문을 해석하지 못하면 cause 를 실어 보낸다")
    void 파싱_실패는_cause_를_싣는다() {
        assertThatThrownBy(() -> decoder.decode("PalmClient#verify()", response(400, "not json at all")))
                .isInstanceOf(UpstreamCallException.class)
                .satisfies(thrown -> {
                    UpstreamCallException ex = (UpstreamCallException) thrown;
                    assertThat(ex.getUpstreamStatus()).isEqualTo(400);
                    assertThat(ex.getCause())
                            .as("이쪽은 우리 파싱 코드가 원인일 수 있어 스택트레이스가 단서가 된다")
                            .isNotNull();
                });
    }

    @Test
    @DisplayName("해석 가능한 4xx 는 CustomFeignException 이다 — 상태 코드를 삼키지 않는다")
    void 해석_가능한_4xx() {
        Exception result = decoder.decode("PalmClient#verify()", response(400, """
                {"type":"about:blank","title":"Palm Not Found","status":400,"detail":"no palm"}
                """));

        assertThat(result).isInstanceOf(CustomFeignException.class);
    }

    /**
     * 실제 HTTP 응답처럼 본문을 <b>한 번만</b> 읽을 수 있는 응답 (UG-362). 문자열 본문은 다시 읽을 수 있어서 두 판정이
     * 각자 스트림을 여는 결함을 잡지 못한다.
     */
    private static Response streamResponse(int status, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response(status, "").toBuilder().body(new ByteArrayInputStream(bytes), bytes.length).build();
    }

    // FeignBlockingLoadBalancerClient 가 합성하는 본문 그대로다 (spring-cloud-openfeign-core 4.1.4·4.3.1·4.3.2 동일).
    private static final String LB_BODY = "Load balancer does not contain an instance for the service match-server";

    /**
     * 문구를 상수끼리 비교하지 않고 <b>라이브러리가 실제로 합성한 응답</b>으로 확인한다 (UG-362 반박 리뷰 L2).
     *
     * <p>초판은 상수를 같은 문자열 리터럴과 비교했다 — 라이브러리가 문구를 바꿔도 초록인 동어반복이었다. 여기서는 진짜
     * {@link FeignBlockingLoadBalancerClient} 에 인스턴스를 하나도 고르지 못하는 {@link LoadBalancerClient} 를 물려
     * {@code execute} 를 돌리고, 그 응답을 그대로 디코더에 넣는다. 의존성을 올리며 문구가 바뀌면 이 테스트가 깨진다.
     */
    @Test
    @DisplayName("UG-362: 로드밸런서가 실제로 합성한 503 이 우리 판정 문구로 시작하고, 디코더가 닿지 않음으로 읽는다")
    void 라이브러리가_합성한_응답으로_판정() throws IOException {
        LoadBalancerClient loadBalancer = mock(LoadBalancerClient.class);
        when(loadBalancer.choose(eq("match-server"), any())).thenReturn(null);
        LoadBalancerClientFactory factory = mock(LoadBalancerClientFactory.class);
        when(factory.getProperties("match-server")).thenReturn(new LoadBalancerProperties());
        Client delegate = mock(Client.class);
        FeignBlockingLoadBalancerClient client =
                new FeignBlockingLoadBalancerClient(delegate, loadBalancer, factory, List.of());

        Response synthesized = client.execute(
                Request.create(Request.HttpMethod.POST, "http://match-server/api/v1/identify",
                        Collections.emptyMap(), "{}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, null),
                new Request.Options());

        // 인스턴스가 없으면 요청을 보내지 않는다 — "닿지 않았다" 의 근거다.
        verifyNoInteractions(delegate);
        assertThat(synthesized.status()).isEqualTo(503);
        try (InputStream in = synthesized.body().asInputStream()) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .startsWith(CommonErrorDecoder.NO_INSTANCE_PREFIX);
        }
        UpstreamCallException ex = (UpstreamCallException) decoder.decode("MatchFeign#identify()", synthesized);
        assertThat(ex.isNotDelivered()).isTrue();
    }

    @Test
    @DisplayName("UG-362: 로드밸런서에 인스턴스가 없어 만든 503 은 닿지 않음이다 — ErrorType·upstreamStatus 는 그대로")
    void 인스턴스_없음은_닿지_않음() {
        Exception result = decoder.decode("MatchFeign#identify(IdentifyFeignRequestDTO)", response(503, LB_BODY));

        assertThat(result).isInstanceOf(UpstreamCallException.class);
        UpstreamCallException ex = (UpstreamCallException) result;
        assertThat(ex.isNotDelivered()).isTrue();
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR);
        assertThat(ex.getUpstreamStatus()).isEqualTo(503);
        assertThat(ex.getOperation()).isEqualTo("MatchFeign#identify(IdentifyFeignRequestDTO)");
    }

    @Test
    @DisplayName("UG-362: 본문 스트림을 한 번만 읽을 수 있어도 로드밸런서 503 을 판정한다")
    void 일회용_스트림의_인스턴스_없음() {
        UpstreamCallException ex = (UpstreamCallException) decoder.decode("x", streamResponse(503, LB_BODY));

        assertThat(ex.isNotDelivered()).isTrue();
    }

    @ParameterizedTest(name = "body={0}")
    @ValueSource(strings = {
            "<html>503 Service Temporarily Unavailable</html>",
            "",
            "load balancer does not contain an instance for the service match-server",
            "Service Unavailable: Load balancer does not contain an instance for the service match-server"
    })
    @DisplayName("UG-362: 다른 503 은 닿지 않음이 아니다 — 하위가 받았을 수 있다")
    void 다른_503은_닿지_않음이_아니다(String body) {
        UpstreamCallException ex = (UpstreamCallException) decoder.decode("x", response(503, body));

        assertThat(ex.isNotDelivered()).isFalse();
    }

    @ParameterizedTest(name = "status={0}")
    @ValueSource(ints = {500, 502, 504})
    @DisplayName("UG-362: 503 이 아니면 같은 문구여도 닿지 않음이 아니다")
    void 상태가_503이_아니면_닿지_않음이_아니다(int status) {
        UpstreamCallException ex = (UpstreamCallException) decoder.decode("x", response(status, LB_BODY));

        assertThat(ex.isNotDelivered()).isFalse();
    }

    @Test
    @DisplayName("UG-362: 503 본문을 읽다 실패해도 던지지 않고 예전 그대로 UpstreamCallException(503) 이다")
    void 본문_읽기_실패는_예전_그대로() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("connection reset while reading body");
            }
        };

        UpstreamCallException ex = (UpstreamCallException) decoder.decode(
                "x", response(503, "").toBuilder().body(broken, null).build());

        assertThat(ex.isNotDelivered()).isFalse();
        assertThat(ex.getUpstreamStatus()).isEqualTo(503);
    }
}

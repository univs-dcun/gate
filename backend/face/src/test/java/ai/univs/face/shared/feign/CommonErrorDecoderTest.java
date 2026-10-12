package ai.univs.face.shared.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.TemporarilyUnavailableException;
import ai.univs.face.shared.exception.UpstreamCallException;
import ai.univs.face.shared.web.enums.ErrorType;
import feign.Request;
import feign.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
                        "http://face-module/verify",
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
        Exception result = decoder.decode("FaceClient#verify()", response(status, ""));

        assertThat(result).isInstanceOf(UpstreamCallException.class);

        UpstreamCallException ex = (UpstreamCallException) result;
        assertThat(ex.getUpstreamStatus())
                .as("이 값이 사라지면 위쪽에서 502 와 503 을 구분할 수 없다")
                .isEqualTo(status);
        assertThat(ex.getOperation()).isEqualTo("FaceClient#verify()");
        assertThat(ex.getReason()).isEqualTo("test-reason");
        assertThat(ex.getCause())
                .as("하위가 오류를 응답한 경우는 우리 호출 스택에 정보가 없다 — 스택트레이스를 남기지 않는다")
                .isNull();
    }

    private static final String MATCH_BUSY_BODY = """
            {"success":false,"data":null,"errors":{"code":"SWAGGER-006","type":"TEMPORARILY_UNAVAILABLE","message":"busy"}}
            """;

    @Test
    @DisplayName("UG-359: match 의 503 + TEMPORARILY_UNAVAILABLE 은 TemporarilyUnavailableException 이 된다")
    void match_일시_불가는_전용_예외() {
        Exception result = decoder.decode("MatchFeign#verifyById(VerifyByIdFeignRequestDTO)",
                response(503, MATCH_BUSY_BODY));

        // UpstreamCallException 이면 400 SWAGGER-005 로 뭉개져 gate 가 "다시 보내면 되는 실패" 를 알 수 없다
        assertThat(result).isInstanceOf(TemporarilyUnavailableException.class);
        TemporarilyUnavailableException ex = (TemporarilyUnavailableException) result;
        assertThat(ex.getErrorType())
                .as("이력의 실패 유형이 여기서 나온다 (FaceHistoryRecorder.recordFailure)")
                .isEqualTo(ErrorType.TEMPORARILY_UNAVAILABLE);
        assertThat(ex.getOperation()).isEqualTo("MatchFeign#verifyById(VerifyByIdFeignRequestDTO)");
    }

    @ParameterizedTest(name = "body={0}")
    @ValueSource(strings = {
            "<html>503 Service Temporarily Unavailable</html>",
            "",
            "null",
            "{\"success\":false,\"data\":null}",
            "{\"success\":false,\"data\":null,\"errors\":{\"code\":\"SWAGGER-005\",\"type\":\"INTERNAL_SERVER_ERROR\",\"message\":\"x\"}}"
    })
    @DisplayName("UG-359: 다른 503(fxp·프록시·다른 유형)은 예전 그대로 UpstreamCallException — 던지지 않는다")
    void 다른_503은_예전_그대로(String body) {
        Exception result = decoder.decode("ExtractFeign#extract()", response(503, body));

        assertThat(result).isInstanceOf(UpstreamCallException.class);
        assertThat(((UpstreamCallException) result).getUpstreamStatus()).isEqualTo(503);
    }

    @ParameterizedTest(name = "status={0}")
    @ValueSource(ints = {500, 502, 504})
    @DisplayName("UG-359: 503 이 아니면 같은 본문이어도 UpstreamCallException 이다")
    void 상태가_503이_아니면_일반_실패(int status) {
        assertThat(decoder.decode("x", response(status, MATCH_BUSY_BODY)))
                .isInstanceOf(UpstreamCallException.class);
    }

    @Test
    @DisplayName("본문을 해석하지 못하면 cause 를 실어 보낸다")
    void 파싱_실패는_cause_를_싣는다() {
        assertThatThrownBy(() -> decoder.decode("FaceClient#verify()", response(400, "not json at all")))
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
        Exception result = decoder.decode("FaceClient#verify()", response(400, """
                {"success":false,"data":null,"errors":{"code":"ML-101","type":"FACE_NOT_FOUND","message":"no face"}}
                """));

        assertThat(result).isInstanceOf(CustomFeignException.class);
    }

    @Test
    @DisplayName("4xx 인데 errors 가 비어 있어도 NPE 로 500 이 나가지 않는다")
    void errors_없는_4xx() {
        // 델타 리뷰가 실측했다. 예전에는 여기서 NPE 가 나 handleGlobalException 으로 떨어졌고
        // 클라이언트는 90여 줄 스택트레이스와 함께 500 을 받았다. palm 디코더는 같은 상황을
        // 이미 정상 처리하고 있어 face 만 예외였다.
        assertThatThrownBy(() -> decoder.decode("FaceClient#verify()",
                response(400, """
                        {"success":false,"data":null}
                        """)))
                .isInstanceOf(UpstreamCallException.class)
                .satisfies(thrown ->
                        assertThat(((UpstreamCallException) thrown).getUpstreamStatus()).isEqualTo(400));
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

    @Test
    @DisplayName("UG-362: 로드밸런서 문구가 라이브러리와 같다 — 바뀌면 판정이 조용히 꺼진다")
    void 로드밸런서_문구_고정() {
        assertThat(CommonErrorDecoder.NO_INSTANCE_PREFIX)
                .isEqualTo("Load balancer does not contain an instance for the service ");
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

    @Test
    @DisplayName("UG-362: 일회용 스트림 본문이어도 match 의 TEMPORARILY_UNAVAILABLE 신호를 잃지 않는다")
    void 일회용_스트림의_일시_불가() {
        assertThat(decoder.decode("x", streamResponse(503, MATCH_BUSY_BODY)))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }
}

package ai.univs.gate.support.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.web.enums.ErrorType;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;

/**
 * UG-280.
 *
 * <p>이 디코더가 <b>어떤 타입</b>을 돌려주는지가 매칭 이력의 생사를 가른다. 매칭 UseCase 들이
 * {@code noRollbackFor} 에 나열한 타입이 아니면 {@code REQUIRES_NEW} 트랜잭션이 롤백되고, 앞서
 * 저장한 {@code MatchHistory} 행이 사라진다. 그래서 타입 자체를 계약으로 고정한다.
 */
@DisplayName("CommonErrorDecoder 단위 테스트")
class CommonErrorDecoderTest {

    private final CommonErrorDecoder decoder = new CommonErrorDecoder();

    private static Response response(int status, String body) {
        return Response.builder()
                .status(status)
                .reason("test")
                .request(Request.create(
                        Request.HttpMethod.POST,
                        "http://face-service/api/v1/face/identify",
                        Collections.emptyMap(),
                        new byte[0],
                        StandardCharsets.UTF_8,
                        null))
                .headers(Collections.emptyMap())
                .body(body, StandardCharsets.UTF_8)
                .build();
    }

    /**
     * 실제 HTTP 응답처럼 본문을 <b>한 번만</b> 읽을 수 있는 응답 (UG-362). 문자열 본문은 몇 번이고 다시 읽을 수
     * 있어서, 두 판정이 각자 스트림을 여는 결함을 잡지 못한다.
     */
    private static Response streamResponse(int status, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        InputStream once = new ByteArrayInputStream(bytes);
        return response(status, "").toBuilder().body(once, bytes.length).build();
    }

    private static final String OUR_FORMAT_BODY = """
            {"success":false,"data":null,"errors":{"code":"ML-101","type":"FACE_NOT_FOUND","message":"no face"}}
            """;

    @Nested
    @DisplayName("4xx — 하위 서비스가 우리 포맷으로 응답한 경우")
    class ParsableClientError {

        @Test
        @DisplayName("CustomFeignException 으로 변환하고 code·type·message 를 그대로 옮긴다")
        void 우리_포맷은_CustomFeignException() {
            Exception result = decoder.decode("FaceClient#identify()", response(400, OUR_FORMAT_BODY));

            assertThat(result).isInstanceOf(CustomFeignException.class);
            CustomFeignException e = (CustomFeignException) result;
            assertThat(e.getCode()).isEqualTo("ML-101");
            assertThat(e.getType()).isEqualTo("FACE_NOT_FOUND");
            assertThat(e.getMessage()).isEqualTo("no face");
        }

        @Test
        @DisplayName("UG-378: errors.details 는 내부용 upstreamDetails 로만 옮긴다 — 공개 details 는 비어 있다")
        void details_는_upstreamDetails_로() {
            Exception result = decoder.decode("FaceClient#createFace()", response(400, """
                    {"success":false,"data":null,"errors":{"code":"MATCH-003","type":"ALREADY_REGISTERED_DESCRIPTOR",
                     "message":"dup","details":{"faceId":"dup-face"}}}
                    """));

            CustomFeignException e = (CustomFeignException) result;
            assertThat(e.getUpstreamDetails()).containsEntry("faceId", "dup-face");
            assertThat(e.getDetails()).isNull();
        }

        @Test
        @DisplayName("UG-378: details 가 없는 예전 응답은 upstreamDetails 가 null")
        void details_없음() {
            CustomFeignException e = (CustomFeignException) decoder.decode("FaceClient#identify()",
                    response(400, OUR_FORMAT_BODY));

            assertThat(e.getUpstreamDetails()).isNull();
        }

        @Test
        @DisplayName("type 은 LivenessErrorType 판정에 쓰이므로 보존돼야 한다")
        void type_보존() {
            // 이 값이 유실되면 UseCase 가 라이브니스 오류를 흡수하지 못하고 그대로 400 을 낸다
            CustomFeignException e =
                    (CustomFeignException) decoder.decode("x", response(422, OUR_FORMAT_BODY));

            assertThat(e.getType()).isEqualTo("FACE_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("5xx / 3xx — 원격 호출 자체가 실패한 경우")
    class RemoteFailure {

        @ParameterizedTest(name = "status={0}")
        @ValueSource(ints = {500, 502, 503, 504, 301, 302})
        @DisplayName("RemoteCallException 을 반환한다 — noRollbackFor 목록에 있는 타입이어야 이력이 남는다")
        void 원격실패는_RemoteCallException(int status) {
            Exception result = decoder.decode("x", response(status, "<html>Bad Gateway</html>"));

            assertThat(result).isInstanceOf(RemoteCallException.class);
            assertThat(((RemoteCallException) result).getUpstreamStatus()).isEqualTo(status);
        }

        @Test
        @DisplayName("오류 코드는 기존과 같은 PJ-005 를 유지한다 — 클라이언트 계약 불변")
        void 오류코드_불변() {
            // 새 코드를 만들면 PJ-005 로 분기하던 고객 코드가 깨진다. 일부러 같은 코드를 쓴다.
            RemoteCallException e = (RemoteCallException) decoder.decode("x", response(503, ""));

            assertThat(e.getErrorType()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR);
            assertThat(e.getErrorType().getCode()).isEqualTo("PJ-005");
        }
    }

    @Nested
    @DisplayName("UG-359: 503 + TEMPORARILY_UNAVAILABLE — 하위가 '잠시 뒤 다시' 를 알려 온 경우")
    class TemporarilyUnavailable {

        private static final String BUSY_BODY = """
                {"success":false,"data":null,"errors":{"code":"SWAGGER-006","type":"TEMPORARILY_UNAVAILABLE","message":"busy"}}
                """;

        @Test
        @DisplayName("RemoteCallException 이되 ErrorType 이 TEMPORARILY_UNAVAILABLE 이다 — 이력·등록 흐름은 그대로 탄다")
        void 일시_불가는_전용_유형() {
            Exception result = decoder.decode("FaceClient#identify()", response(503, BUSY_BODY));

            // 타입이 RemoteCallException 이어야 유스케이스의 catch 가 failUpstream 으로 이력을 남기고,
            // 등록은 "결과 모름"(UG-338)으로 발급 id 를 남긴다. 새 예외 타입이면 그 catch 를 전부 빠져나간다.
            assertThat(result).isInstanceOf(RemoteCallException.class);
            RemoteCallException e = (RemoteCallException) result;
            assertThat(e.getErrorType()).isEqualTo(ErrorType.TEMPORARILY_UNAVAILABLE);
            assertThat(e.getErrorType().getCode()).isEqualTo("PJ-006");
            assertThat(e.isTemporarilyUnavailable()).isTrue();
            assertThat(e.getUpstreamStatus()).isEqualTo(503);
            assertThat(e.getOperation()).isEqualTo("FaceClient#identify()");
        }

        @Test
        @DisplayName("반박 리뷰 L6: 하위 envelope 에 필드가 늘어도 신호를 잃지 않는다")
        void 필드가_늘어도_일시_불가() {
            String withExtra = """
                    {"success":false,"data":null,"traceId":"t-1","errors":{"code":"SWAGGER-006","type":"TEMPORARILY_UNAVAILABLE","message":"busy","detail":"x"}}
                    """;

            RemoteCallException e = (RemoteCallException) decoder.decode("x", response(503, withExtra));

            assertThat(e.getErrorType()).isEqualTo(ErrorType.TEMPORARILY_UNAVAILABLE);
        }

        @ParameterizedTest(name = "body={0}")
        @ValueSource(strings = {
                "<html>503 Service Temporarily Unavailable</html>",
                "",
                "null",
                "{\"success\":false,\"data\":null}",
                "{\"success\":false,\"data\":null,\"errors\":{\"code\":\"SWAGGER-005\",\"type\":\"INTERNAL_SERVER_ERROR\",\"message\":\"x\"}}",
                "{\"unexpected\":1}"
        })
        @DisplayName("다른 503(프록시 HTML·빈 본문·다른 유형·모르는 모양)은 예전 그대로 — 던지지 않는다")
        void 다른_503은_예전_그대로(String body) {
            Exception result = decoder.decode("x", response(503, body));

            assertThat(result).isInstanceOf(RemoteCallException.class);
            RemoteCallException e = (RemoteCallException) result;
            assertThat(e.getErrorType()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR);
            assertThat(e.getUpstreamStatus()).isEqualTo(503);
        }

        @Test
        @DisplayName("본문이 없는 503 도 예전 그대로다")
        void 본문_없는_503() {
            Response noBody = response(503, "").toBuilder().body((Response.Body) null).build();

            RemoteCallException e = (RemoteCallException) decoder.decode("x", noBody);

            assertThat(e.getErrorType()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR);
        }

        @ParameterizedTest(name = "status={0}")
        @ValueSource(ints = {500, 502, 504})
        @DisplayName("503 이 아니면 같은 본문이어도 일반 하위 실패다 — 계약은 503 + 유형 둘 다다")
        void 상태가_503이_아니면_일반_실패(int status) {
            RemoteCallException e = (RemoteCallException) decoder.decode("x", response(status, BUSY_BODY));

            assertThat(e.getErrorType()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR);
        }
    }

    @Nested
    @DisplayName("UG-362: 로드밸런서가 만든 503 — 요청이 하위에 닿지 않은 경우")
    class NoInstance {

        // FeignBlockingLoadBalancerClient 가 합성하는 본문 그대로다 (spring-cloud-openfeign-core 4.1.4·4.3.1·4.3.2 동일).
        private static final String LB_BODY = "Load balancer does not contain an instance for the service face-service";

        /**
         * 문구를 상수끼리 비교하지 않고 <b>라이브러리가 실제로 합성한 응답</b>으로 확인한다 (UG-362 반박 리뷰 L2).
         *
         * <p>초판은 상수를 같은 문자열 리터럴과 비교했다 — 라이브러리가 문구를 바꿔도 초록인 동어반복이었다. 여기서는 진짜
         * {@link FeignBlockingLoadBalancerClient} 에 인스턴스를 하나도 고르지 못하는 {@link LoadBalancerClient} 를 물려
         * {@code execute} 를 돌리고, 그 응답을 그대로 디코더에 넣는다. 의존성을 올리며 문구가 바뀌면 이 테스트가 깨진다.
         */
        @Test
        @DisplayName("로드밸런서가 실제로 합성한 503 이 우리 판정 문구로 시작하고, 디코더가 닿지 않음으로 읽는다")
        void 라이브러리가_합성한_응답으로_판정() throws IOException {
            LoadBalancerClient loadBalancer = mock(LoadBalancerClient.class);
            when(loadBalancer.choose(eq("face-service"), any())).thenReturn(null);
            LoadBalancerClientFactory factory = mock(LoadBalancerClientFactory.class);
            when(factory.getProperties("face-service")).thenReturn(new LoadBalancerProperties());
            Client delegate = mock(Client.class);
            FeignBlockingLoadBalancerClient client =
                    new FeignBlockingLoadBalancerClient(delegate, loadBalancer, factory, List.of());

            Response synthesized = client.execute(
                    Request.create(Request.HttpMethod.POST, "http://face-service/api/v1/face/identify",
                            Collections.emptyMap(), "{}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, null),
                    new Request.Options());

            // 인스턴스가 없으면 요청을 보내지 않는다 — "닿지 않았다" 의 근거다.
            verifyNoInteractions(delegate);
            assertThat(synthesized.status()).isEqualTo(503);
            try (InputStream in = synthesized.body().asInputStream()) {
                assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                        .startsWith(CommonErrorDecoder.NO_INSTANCE_PREFIX);
            }
            RemoteCallException e = (RemoteCallException) decoder.decode("FaceClient#identify()", synthesized);
            assertThat(e.isNotDelivered()).isTrue();
        }

        @Test
        @DisplayName("닿지 않음으로 표시하되 ErrorType·upstreamStatus 는 예전 그대로다 — 이력이 바뀌지 않는다")
        void 인스턴스_없음은_닿지_않음() {
            Exception result = decoder.decode("FaceClient#identify()", response(503, LB_BODY));

            assertThat(result).isInstanceOf(RemoteCallException.class);
            RemoteCallException e = (RemoteCallException) result;
            assertThat(e.isNotDelivered()).isTrue();
            assertThat(e.isTemporarilyUnavailable()).isFalse();
            // failUpstream 이 이 두 값을 이력에 남긴다. 예전(RemoteCallException(503))과 같아야 한다.
            assertThat(e.getErrorType()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR);
            assertThat(e.getUpstreamStatus()).isEqualTo(503);
            assertThat(e.getOperation()).isEqualTo("FaceClient#identify()");
        }

        @Test
        @DisplayName("본문 스트림을 한 번만 읽을 수 있어도 판정한다")
        void 일회용_스트림에서도_판정() {
            RemoteCallException e = (RemoteCallException) decoder.decode("x", streamResponse(503, LB_BODY));

            assertThat(e.isNotDelivered()).isTrue();
        }

        @ParameterizedTest(name = "status={0}")
        @ValueSource(ints = {500, 502, 504})
        @DisplayName("503 이 아니면 같은 문구여도 일반 하위 실패다")
        void 상태가_503이_아니면_일반_실패(int status) {
            RemoteCallException e = (RemoteCallException) decoder.decode("x", response(status, LB_BODY));

            assertThat(e.isNotDelivered()).isFalse();
        }

        @ParameterizedTest(name = "body={0}")
        @ValueSource(strings = {
                "<html>503 Service Temporarily Unavailable</html>",
                "",
                "load balancer does not contain an instance for the service face-service",
                "Service Unavailable: Load balancer does not contain an instance for the service face-service"
        })
        @DisplayName("다른 503 은 닿지 않음으로 보지 않는다 — 하위가 받았을 수 있다")
        void 다른_503은_닿지_않음이_아니다(String body) {
            RemoteCallException e = (RemoteCallException) decoder.decode("x", response(503, body));

            assertThat(e.isNotDelivered()).isFalse();
            assertThat(e.getErrorType()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR);
        }
    }

    @Nested
    @DisplayName("UG-362: 503 본문은 한 번만 읽는다")
    class SingleRead {

        @Test
        @DisplayName("일회용 스트림 본문이어도 TEMPORARILY_UNAVAILABLE 신호를 잃지 않는다")
        void 일회용_스트림의_일시_불가() {
            String busy = """
                    {"success":false,"data":null,"errors":{"code":"SWAGGER-006","type":"TEMPORARILY_UNAVAILABLE","message":"busy"}}
                    """;

            RemoteCallException e = (RemoteCallException) decoder.decode("x", streamResponse(503, busy));

            assertThat(e.isTemporarilyUnavailable()).isTrue();
            assertThat(e.isNotDelivered()).isFalse();
        }

        @Test
        @DisplayName("본문을 읽다 실패해도 던지지 않고 일반 하위 실패다")
        void 읽기_실패는_일반_실패() {
            InputStream broken = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("connection reset while reading body");
                }
            };
            Response r = response(503, "").toBuilder().body(broken, null).build();

            RemoteCallException e = (RemoteCallException) decoder.decode("x", r);

            assertThat(e.getErrorType()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR);
            assertThat(e.isNotDelivered()).isFalse();
            assertThat(e.getUpstreamStatus()).isEqualTo(503);
        }
    }

    @Nested
    @DisplayName("4xx 인데 본문이 우리 포맷이 아닌 경우")
    class UnparsableClientError {

        @Test
        @DisplayName("RemoteCallException 을 던진다 — 이 경로도 이력이 남아야 원인을 추적할 수 있다")
        void 파싱실패도_RemoteCallException() {
            // 예전에는 CustomGateException 이라 롤백됐다. 게이트웨이·프록시가 끼어들어
            // 우리 포맷이 아닌 4xx 를 반환하는 경우가 실제로 있다.
            assertThatThrownBy(() -> decoder.decode("x", response(404, "Not Found")))
                    .isInstanceOf(RemoteCallException.class)
                    .extracting(e -> ((RemoteCallException) e).getUpstreamStatus())
                    .isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("4xx 인데 본문에 errors 가 없는 경우 (UG-280 반박 리뷰)")
    class MissingErrors {

        @Test
        @DisplayName("우리 envelope 모양이지만 errors 가 비면 RemoteCallException 이다 — NPE 로 이력이 사라지지 않는다")
        void errors_없으면_RemoteCallException() {
            // 프록시·사이드카가 같은 포맷으로 {"success":false,"data":null} 만 반환하는 경우.
            // 알려진 필드만 있어 파싱은 성공하지만 errors 가 null 이라 예전에는 여기서 NPE 가 났고,
            // NPE 는 noRollbackFor 에 걸리지 않아 매칭 이력 행이 롤백됐다.
            Exception result = decoder.decode("x", response(403, "{\"success\":false,\"data\":null}"));

            assertThat(result).isInstanceOf(RemoteCallException.class);
            assertThat(((RemoteCallException) result).getUpstreamStatus()).isEqualTo(403);
        }

        @Test
        @DisplayName("methodKey 를 보존한다 — 상태 코드만으로는 어느 하위 서비스인지 알 수 없다")
        void methodKey_보존() {
            RemoteCallException e =
                    (RemoteCallException) decoder.decode("PalmClient#identify()", response(502, ""));

            assertThat(e.getOperation()).isEqualTo("PalmClient#identify()");
        }
    }
}

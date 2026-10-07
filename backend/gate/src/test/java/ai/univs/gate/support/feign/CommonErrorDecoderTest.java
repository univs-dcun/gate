package ai.univs.gate.support.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.web.enums.ErrorType;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

package ai.univs.gate.support.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.feign.dto.FeignResponseApi;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * UG-280 반박 리뷰에서 나온 BLOCKER 의 회귀 테스트.
 *
 * <p>{@code CommonErrorDecoder} 는 상태 코드 300 이상의 <b>응답이 도착했을 때만</b> 불린다. 연결
 * 거부·읽기 타임아웃·연결 리셋은 응답이 없어 Feign 이 {@link RetryableException} 을 던지고, 이
 * 프로젝트에는 {@code Retryer} 빈이 없으므로 그대로 호출자까지 올라온다. 그 예외는
 * {@code BusinessException} 계열이 아니라 매칭 UseCase 의 {@code noRollbackFor} 에 걸리지 않으므로,
 * <b>UG-280 이 고치려던 증상이 가장 흔한 장애 형태에서 그대로 남아 있었다.</b>
 */
@DisplayName("RemoteCalls 단위 테스트")
class RemoteCallsTest {

    private static RetryableException timeout() {
        Request request = Request.create(
                Request.HttpMethod.POST,
                "http://face-service/api/v1/face/identify",
                Collections.emptyMap(),
                new byte[0],
                StandardCharsets.UTF_8,
                null);
        return new RetryableException(
                -1, "read timed out", Request.HttpMethod.POST,
                new SocketTimeoutException("Read timed out"), (Long) null, request);
    }

    @Test
    @DisplayName("응답을 받지 못한 실패는 RemoteCallException(NO_RESPONSE) 로 번역된다")
    void 타임아웃은_RemoteCallException() {
        RetryableException cause = timeout();

        assertThatThrownBy(() -> RemoteCalls.of("face.identify", () -> {
            throw cause;
        }))
                .isInstanceOf(RemoteCallException.class)
                .satisfies(e -> {
                    RemoteCallException r = (RemoteCallException) e;
                    assertThat(r.isNoResponse()).isTrue();
                    assertThat(r.getUpstreamStatus()).isEqualTo(RemoteCallException.NO_RESPONSE);
                    // 상태 코드가 없으므로 어느 호출이 끊겼는지는 이 값만이 알려준다
                    assertThat(r.getOperation()).isEqualTo("face.identify");
                    assertThat(r.getCause()).isSameAs(cause);
                });
    }

    @Test
    @DisplayName("void 호출도 동일하게 번역된다")
    void void_호출도_번역() {
        assertThatThrownBy(() -> RemoteCalls.run("face.deleteFace", () -> {
            throw timeout();
        }))
                .isInstanceOf(RemoteCallException.class);
    }

    @Test
    @DisplayName("4xx 로 만들어진 CustomFeignException 은 건드리지 않고 그대로 통과시킨다")
    void 다른_예외는_통과() {
        // 라이브니스 오류 흡수는 UseCase 가 e.getType() 으로 판정한다. 여기서 감싸면 그 판정이 깨진다.
        CustomFeignException feign = new CustomFeignException("ML-101", "FACE_NOT_FOUND", "no face");

        assertThatThrownBy(() -> RemoteCalls.of("face.identify", () -> {
            throw feign;
        }))
                .isSameAs(feign);
    }

    @Test
    @DisplayName("정상 응답은 값을 그대로 돌려주고 호출을 한 번만 한다")
    void 정상_경로() {
        AtomicInteger calls = new AtomicInteger();

        String result = RemoteCalls.of("face.createFace", () -> {
            calls.incrementAndGet();
            return "face-id-1";
        });

        assertThat(result).isEqualTo("face-id-1");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("UG-280 재검증: 디코딩 실패(FeignException)도 번역한다 — 200 응답에서도 발생한다")
    void 디코딩_실패도_번역() {
        // ResponseHandler.handleResponse 가 IOException 을 FeignException.errorReading() 으로
        // 감싼다. RetryableException 이 아니므로 예전에는 그대로 빠져나가 이력 행이 사라졌다.
        // 프록시가 본문을 잘라 보내면 200 응답에서도 일어난다.
        // FeignException.errorReading 은 패키지 전용이라 직접 못 만든다. 같은 계층의
        // 공개 팩터리로 대체한다 — 잡히는 조건은 FeignException 여부이므로 동등하다.
        FeignException decodeFailure = FeignException.errorStatus(
                "FaceClient#identify()",
                feign.Response.builder()
                        .status(200)
                        .reason("OK")
                        .request(Request.create(Request.HttpMethod.POST, "http://face-service/x",
                                Collections.emptyMap(), new byte[0], StandardCharsets.UTF_8, null))
                        .headers(Collections.emptyMap())
                        .body("truncated", StandardCharsets.UTF_8)
                        .build());

        assertThatThrownBy(() -> RemoteCalls.of("face.identify", () -> {
            throw decodeFailure;
        }))
                .isInstanceOf(RemoteCallException.class)
                .extracting(e -> ((RemoteCallException) e).isNoResponse())
                .isEqualTo(true);
    }

    @Test
    @DisplayName("우리 코드의 버그는 통과시킨다 — 원격 실패로 분류하면 원인이 가려진다")
    void 우리_버그는_통과() {
        IllegalStateException boom = new IllegalStateException("boom");

        assertThatThrownBy(() -> RemoteCalls.of("face.identify", () -> {
            throw boom;
        }))
                .isSameAs(boom);
    }

    @Test
    @DisplayName("UG-280 재검증: 200 응답에 data 가 없으면 NPE 대신 RemoteCallException 이다")
    void data_없으면_RemoteCallException() {
        // {"success":false,"data":null} 을 200 으로 받으면 예전에는 .getFaceId() 에서 NPE 가 났고,
        // NPE 는 noRollbackFor 에 걸리지 않아 매칭 이력 행이 사라졌다.
        FeignResponseApi<String> empty = new FeignResponseApi<>(false, null, null);

        assertThatThrownBy(() -> RemoteCalls.data("face.createFace", () -> empty))
                .isInstanceOf(RemoteCallException.class)
                .extracting(e -> ((RemoteCallException) e).getOperation())
                .isEqualTo("face.createFace");
    }

    @Test
    @DisplayName("data 가 있으면 그 값을 그대로 돌려준다")
    void data_정상() {
        FeignResponseApi<String> ok = new FeignResponseApi<>(true, "face-id-1", null);

        assertThat(RemoteCalls.data("face.createFace", () -> ok)).isEqualTo("face-id-1");
    }

    /** 본문이 실린 요청 — Feign 이 스트리밍으로 보내 JDK 가 몰래 다시 보내지 않는다 (UG-362 반박 리뷰). */
    private static final byte[] 본문 = "{\"image\":\"AAAA\"}".getBytes(StandardCharsets.UTF_8);

    private static RetryableException retryable(Throwable cause) {
        return retryable(cause, Request.HttpMethod.POST, 본문);
    }

    private static RetryableException retryable(Throwable cause, Request.HttpMethod method, byte[] body) {
        return new RetryableException(
                -1, cause.getMessage(), method, cause, (Long) null,
                Request.create(method, "http://face-service/x",
                        Collections.emptyMap(), body, StandardCharsets.UTF_8, null));
    }

    static Stream<Throwable> 닿지_않은_원인() {
        return Stream.of(
                new ConnectException("Connection refused"),
                new NoRouteToHostException("No route to host"),
                // 클라이언트 라이브러리가 한 번 더 감싸도 사슬에서 찾는다
                new IOException("connect failed", new ConnectException("Connection refused")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("닿지_않은_원인")
    @DisplayName("UG-362: 연결 거부·경로 없음은 닿지 않음으로 표시하고 ErrorType·NO_RESPONSE 는 그대로다")
    void 연결_실패는_닿지_않음(Throwable cause) {
        RetryableException retryable = retryable(cause);

        assertThatThrownBy(() -> RemoteCalls.of("face.identify", () -> {
            throw retryable;
        }))
                .isInstanceOf(RemoteCallException.class)
                .satisfies(e -> {
                    RemoteCallException r = (RemoteCallException) e;
                    assertThat(r.isNotDelivered()).isTrue();
                    // 이력(failUpstream)이 남기는 값은 예전과 같아야 한다
                    assertThat(r.getErrorType()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR);
                    assertThat(r.isNoResponse()).isTrue();
                    assertThat(r.getOperation()).isEqualTo("face.identify");
                    assertThat(r.getCause()).isSameAs(retryable);
                });
    }

    static Stream<Throwable> 결과를_모르는_원인() {
        return Stream.of(
                // 연결 타임아웃과 읽기 타임아웃은 같은 타입이라 가를 수 없다 — 읽기 타임아웃이면 하위가 등록을 끝냈을 수 있다
                new SocketTimeoutException("Read timed out"),
                new SocketTimeoutException("Connect timed out"),
                new SocketException("Connection reset"),
                new UnknownHostException("face-service"),
                new IOException("Connection refused"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("결과를_모르는_원인")
    @DisplayName("UG-362: 타임아웃·연결 리셋·이름 해석 실패는 예전 그대로 — 닿지 않았다고 단정하지 않는다")
    void 결과를_모르면_예전_그대로(Throwable cause) {
        RetryableException retryable = retryable(cause);

        assertThatThrownBy(() -> RemoteCalls.of("face.createFace", () -> {
            throw retryable;
        }))
                .isInstanceOf(RemoteCallException.class)
                .satisfies(e -> {
                    RemoteCallException r = (RemoteCallException) e;
                    assertThat(r.isNotDelivered()).isFalse();
                    assertThat(r.isNoResponse()).isTrue();
                });
    }

    static Stream<Arguments> 본문_없는_요청() {
        return 닿지_않은_원인().flatMap(cause -> Stream.of(
                Arguments.of(Request.HttpMethod.GET, cause),
                Arguments.of(Request.HttpMethod.DELETE, cause)));
    }

    /**
     * UG-362 반박 리뷰 M1. JDK {@code HttpURLConnection} 은 응답 읽기에 실패한 본문 없는 요청을 새 연결로 몰래 한 번 더
     * 보낸다. 하위가 DELETE 를 처리한 뒤 죽으면 호출자는 재전송이 맞은 {@code ConnectException} 을 본다 — 이미 지워졌는데
     * "닿지 않았다, 다시 보내라" 가 되면 안 된다 (JDK 21 실측: GET·DELETE → ConnectException, 본문 있는 POST → EOF).
     */
    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("본문_없는_요청")
    @DisplayName("UG-362: 본문 없는 요청(GET·DELETE)의 연결 거부는 닿지 않음이 아니다 — JDK 가 몰래 다시 보냈을 수 있다")
    void 본문_없는_요청은_예전_그대로(Request.HttpMethod method, Throwable cause) {
        RetryableException retryable = retryable(cause, method, null);

        assertThatThrownBy(() -> RemoteCalls.of("face.deleteFace", () -> {
            throw retryable;
        }))
                .isInstanceOf(RemoteCallException.class)
                .satisfies(e -> {
                    RemoteCallException r = (RemoteCallException) e;
                    assertThat(r.isNotDelivered()).isFalse();
                    assertThat(r.isNoResponse()).isTrue();
                    assertThat(r.getCause()).isSameAs(retryable);
                });
    }

    @Test
    @DisplayName("UG-362: 빈 본문(byte[0])도 본문 없음으로 친다 — 보수적 경계")
    void 빈_본문은_예전_그대로() {
        RetryableException retryable =
                retryable(new ConnectException("Connection refused"), Request.HttpMethod.POST, new byte[0]);

        assertThatThrownBy(() -> RemoteCalls.of("face.identify", () -> {
            throw retryable;
        }))
                .isInstanceOf(RemoteCallException.class)
                .extracting(e -> ((RemoteCallException) e).isNotDelivered())
                .isEqualTo(false);
    }

    @Test
    @DisplayName("UG-362: 요청 정보가 없으면 판단하지 않는다 — 예전 그대로")
    void 요청_정보가_없으면_예전_그대로() {
        // 공개 생성자는 request 가 null 이면 던지므로, request() 만 null 을 돌려주게 덮는다.
        RetryableException noRequest = new RetryableException(
                -1, "refused", Request.HttpMethod.POST, new ConnectException("Connection refused"), (Long) null,
                Request.create(Request.HttpMethod.POST, "http://face-service/x",
                        Collections.emptyMap(), 본문, StandardCharsets.UTF_8, null)) {
            @Override
            public Request request() {
                return null;
            }
        };

        assertThatThrownBy(() -> RemoteCalls.of("face.identify", () -> {
            throw noRequest;
        }))
                .isInstanceOf(RemoteCallException.class)
                .extracting(e -> ((RemoteCallException) e).isNotDelivered())
                .isEqualTo(false);
    }

    @Test
    @DisplayName("UG-362: RetryableException 이 아닌 FeignException 은 원인 사슬에 연결 실패가 있어도 닿지 않음이 아니다")
    void 디코딩_실패는_닿지_않음이_아니다() {
        // 응답을 받은 뒤의 실패다 — 요청은 하위에 닿았다.
        FeignException decodeFailure = new FeignException.InternalServerError(
                "decode", Request.create(Request.HttpMethod.POST, "http://face-service/x",
                        Collections.emptyMap(), new byte[0], StandardCharsets.UTF_8, null),
                new byte[0], Collections.emptyMap());
        decodeFailure.initCause(new ConnectException("unrelated"));

        assertThatThrownBy(() -> RemoteCalls.of("face.identify", () -> {
            throw decodeFailure;
        }))
                .isInstanceOf(RemoteCallException.class)
                .extracting(e -> ((RemoteCallException) e).isNotDelivered())
                .isEqualTo(false);
    }

    @Test
    @DisplayName("IOException 원인이 무엇이든 NO_RESPONSE 로 취급한다")
    void 원인_무관() {
        RetryableException connectionRefused = new RetryableException(
                -1, "connection refused", Request.HttpMethod.POST,
                new IOException("Connection refused"), (Long) null,
                Request.create(Request.HttpMethod.POST, "http://palm-service/x",
                        Collections.emptyMap(), new byte[0], StandardCharsets.UTF_8, null));

        assertThatThrownBy(() -> RemoteCalls.of("palm.identify", () -> {
            throw connectionRefused;
        }))
                .isInstanceOf(RemoteCallException.class)
                .extracting(e -> ((RemoteCallException) e).isNoResponse())
                .isEqualTo(true);
    }
}

package ai.univs.gate.support.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
import feign.Request;
import feign.RetryableException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("IOException 을 감싸지 않은 RuntimeException 은 통과시킨다")
    void 예상외_런타임예외는_통과() {
        IllegalStateException boom = new IllegalStateException("boom");

        assertThatThrownBy(() -> RemoteCalls.of("face.identify", () -> {
            throw boom;
        }))
                .isSameAs(boom);
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

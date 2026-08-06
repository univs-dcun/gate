package ai.univs.face.shared.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.UpstreamCallException;
import feign.Request;
import feign.Response;
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
}

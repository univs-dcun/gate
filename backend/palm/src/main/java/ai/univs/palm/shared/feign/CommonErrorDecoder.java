package ai.univs.palm.shared.feign;

import ai.univs.palm.shared.exception.CustomFeignException;
import ai.univs.palm.shared.exception.UpstreamCallException;
import ai.univs.palm.shared.feign.dto.ProblemDetailFeignResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class CommonErrorDecoder implements ErrorDecoder {

    private static final int SERVICE_UNAVAILABLE = 503;

    /** 503 본문을 판정하려고 읽는 최대 바이트 (UG-362). {@link #readBodyQuietly} 참고. */
    static final int MAX_503_BODY_BYTES = 64 * 1024;

    /** Spring Cloud LoadBalancer 가 인스턴스 없음을 알릴 때 합성하는 본문의 앞부분 (UG-362). gate·face 와 같은 값이다. */
    static final String NO_INSTANCE_PREFIX = "Load balancer does not contain an instance for the service ";

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * <b>여기서는 로그를 남기지 않는다</b> (UG-299 반박 리뷰).
     *
     * <p>처음에는 여기서 {@code log.error} 를 찍었는데, 그러면 한 번의 실패에 ERROR 두 줄이
     * 나간다 — 여기서 한 줄, 이 예외를 받은 {@code GlobalExceptionHandler} 가 스택트레이스와
     * 함께 또 한 줄. 대신 상태 코드를 {@link UpstreamCallException} 에 실어 보내고 기록은
     * 핸들러에 맡긴다. gate 의 {@code RemoteCallException} 과 같은 모양이다.
     */
    @Override
    public Exception decode(String s, Response response) {
        int status = response.status();

        if (status >= 400 && status < 500) {
            ProblemDetailFeignResponseDTO problem = parseProblemDetail(s, response);
            String code = "PALM-" + status;
            String type = problem.getTitle() != null
                    ? problem.getTitle().toUpperCase().replace(" ", "_")
                    : "PALM_MODULE_ERROR";
            String message = problem.getDetail() != null
                    ? problem.getDetail()
                    : "Palm module error occurred";
            return new CustomFeignException(code, type, message);
        }

        // UG-362: 팜 모듈이 아니라 우리 쪽 로드밸런서가 만든 503 이다 — 인스턴스가 없어 요청을 보내지도 않았다.
        // upstreamStatus 는 예전과 같은 503 이고, 핸들러가 503 SWAGGER-006 으로 내보낸다. 지금 PalmFeign 은 url 고정이라
        // 로드밸런서를 거치지 않지만 face 와 규칙을 맞춰 둔다 — 본문을 읽는 것은 503 일 때뿐이다.
        if (status == SERVICE_UNAVAILABLE && isNoInstance(readBodyQuietly(response))) {
            return UpstreamCallException.notDelivered(status, s, "로드밸런서에 인스턴스 없음");
        }

        // 3xx or 5xx. 3xx 도 여기로 온다 — Feign 은 리다이렉트를 따라가지 않으므로 호출은
        // 실패한 것이지만 "죽었다" 와는 성격이 다르다. 그래서 메시지에 상태 코드를 그대로
        // 싣고 단정적인 표현을 쓰지 않는다.
        return new UpstreamCallException(status, s, response.reason());
    }

    /**
     * 503 본문을 바이트로 한 번 읽는다 (UG-362). <b>절대 던지지 않는다</b> — 읽지 못하면 빈 배열이다.
     *
     * <p>503 은 팜 모듈·프록시도 낸다. 본문이 HTML 이거나 비어 있거나 아예 없는 것이 정상이고, 그때는 예전과 같은
     * {@link UpstreamCallException} 으로 떨어져야 한다. 판정에 필요한 것은 앞부분뿐이라 상한({@link #MAX_503_BODY_BYTES})을
     * 둔다.
     */
    private static byte[] readBodyQuietly(Response response) {
        if (response.body() == null) {
            return new byte[0];
        }
        try (InputStream in = response.body().asInputStream()) {
            return in.readNBytes(MAX_503_BODY_BYTES);
        } catch (Exception e) {
            return new byte[0];
        }
    }

    /**
     * Spring Cloud LoadBalancer 가 대상 인스턴스를 찾지 못해 <b>스스로 만든</b> 503 인가 (UG-362).
     *
     * <p>{@code FeignBlockingLoadBalancerClient}(재시도판도 같다)는 인스턴스가 없으면 요청을 보내지 않고 상태 503,
     * 평문 본문 {@value #NO_INSTANCE_PREFIX}{@code <serviceId>} 인 응답을 합성한다. spring-cloud-openfeign-core
     * 4.1.4·4.3.1·4.3.2 에서 문구가 같음을 확인했다. 문구가 바뀌면 판정이 거짓이 되어 예전 동작으로 돌아간다.
     */
    private static boolean isNoInstance(byte[] body) {
        return new String(body, StandardCharsets.UTF_8).startsWith(NO_INSTANCE_PREFIX);
    }

    private ProblemDetailFeignResponseDTO parseProblemDetail(String methodKey, Response response) {
        try {
            return mapper.readValue(response.body().asInputStream(), ProblemDetailFeignResponseDTO.class);
        } catch (Exception e) {
            // 4xx 인데 본문이 ProblemDetail 이 아니다. 하위 모듈이 계약을 바꿨거나 프록시가
            // 끼어든 것이므로 우리 쪽 문제로 올린다. 이쪽은 파싱이 우리 코드라 스택트레이스가
            // 단서가 되므로 cause 를 실어 보낸다 (핸들러가 그때만 스택트레이스를 남긴다).
            throw new UpstreamCallException(response.status(), methodKey, "응답 해석 실패", e);
        }
    }
}

package ai.univs.face.shared.feign;

import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.UpstreamCallException;
import ai.univs.face.shared.feign.dto.FeignErrors;
import ai.univs.face.shared.feign.dto.FeignResponseApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;

public class CommonErrorDecoder implements ErrorDecoder {

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
            FeignResponseApi<?> feignResponse = parseFeignResponse(s, response);
            FeignErrors feignErrors = feignResponse.getErrors();
            return new CustomFeignException(feignErrors.getCode(), feignErrors.getType(), feignErrors.getMessage());
        }

        // 3xx or 5xx. 3xx 도 여기로 온다 — Feign 은 리다이렉트를 따라가지 않으므로 호출은
        // 실패한 것이지만 "죽었다" 와는 성격이 다르다. 그래서 메시지에 상태 코드를 그대로
        // 싣고 단정적인 표현을 쓰지 않는다.
        return new UpstreamCallException(status, s, response.reason());
    }

    private FeignResponseApi<?> parseFeignResponse(String methodKey, Response response) {
        try {
            return mapper.readValue(response.body().asInputStream(), FeignResponseApi.class);
        } catch (Exception e) {
            // 4xx 인데 본문이 우리가 아는 포맷이 아니다. 하위 모듈이 계약을 바꿨거나 프록시가
            // 끼어든 것이므로 우리 쪽 문제로 올린다. 이쪽은 파싱이 우리 코드라 스택트레이스가
            // 단서가 되므로 cause 를 실어 보낸다 (핸들러가 그때만 스택트레이스를 남긴다).
            throw new UpstreamCallException(response.status(), methodKey, "응답 해석 실패", e);
        }
    }
}

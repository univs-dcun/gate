package ai.univs.face.shared.feign;

import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.TemporarilyUnavailableException;
import ai.univs.face.shared.exception.UpstreamCallException;
import ai.univs.face.shared.feign.dto.FeignErrors;
import ai.univs.face.shared.feign.dto.FeignResponseApi;
import ai.univs.face.shared.web.enums.ErrorType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;

public class CommonErrorDecoder implements ErrorDecoder {

    private static final int SERVICE_UNAVAILABLE = 503;

    // UG-359 반박 리뷰 L6: 하위 envelope 에 필드가 하나 늘어도(FAIL_ON_UNKNOWN_PROPERTIES 기본 true) 파싱이 실패하지 않게 한다.
    // 실패하면 4xx 의 code 전달과 503 「일시 불가」 신호가 조용히 400 PJ-005 로 떨어진다.
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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

            // 본문이 우리 포맷으로 파싱은 됐는데 errors 가 비어 있는 경우 (델타 리뷰 지적).
            // 예전에는 여기서 곧바로 NPE 가 나 handleGlobalException 으로 떨어졌다 — ERROR 에
            // 90여 줄 스택트레이스가 붙고 클라이언트는 500 을 받았다. palm 쪽 디코더는 같은
            // 상황을 이미 정상 처리하고 있어 face 만 예외였다. gate 의 RemoteCallException
            // javadoc 도 이 경우를 명시적으로 덮는다고 적고 있다.
            if (feignErrors == null) {
                throw new UpstreamCallException(status, s, "errors 없는 4xx 응답");
            }

            return new CustomFeignException(feignErrors.getCode(), feignErrors.getType(), feignErrors.getMessage());
        }

        // UG-359: match 가 503 + TEMPORARILY_UNAVAILABLE 로 "잠시 뒤 다시" 를 알려 온 경우만 따로 가른다.
        // 전용 핸들러가 503 + SWAGGER-006 으로 내보내 gate 까지 신호가 그대로 올라간다.
        if (status == SERVICE_UNAVAILABLE && isTemporarilyUnavailable(response)) {
            return new TemporarilyUnavailableException(s);
        }

        // 3xx or 5xx. 3xx 도 여기로 온다 — Feign 은 리다이렉트를 따라가지 않으므로 호출은
        // 실패한 것이지만 "죽었다" 와는 성격이 다르다. 그래서 메시지에 상태 코드를 그대로
        // 싣고 단정적인 표현을 쓰지 않는다.
        return new UpstreamCallException(status, s, response.reason());
    }

    /**
     * 503 본문이 우리 envelope 이고 {@code errors.type} 이 {@code TEMPORARILY_UNAVAILABLE} 인가 (UG-359).
     *
     * <p><b>여기서는 절대 던지지 않는다.</b> 503 은 ML 모듈(fxp)·프록시도 낸다 — 본문이 HTML 이거나 비어 있는 것이
     * 정상이다. 해석에 실패하면 예전과 같은 {@link UpstreamCallException} 으로 떨어져야 한다. 그래서 던지는
     * {@link #parseFeignResponse} 를 재사용하지 않는다. 유형 이름만 본다 — 그것이 match 와의 계약이다.
     */
    private boolean isTemporarilyUnavailable(Response response) {
        if (response.body() == null) {
            return false;
        }
        try {
            FeignResponseApi<?> feignResponse =
                    mapper.readValue(response.body().asInputStream(), FeignResponseApi.class);
            FeignErrors errors = feignResponse == null ? null : feignResponse.getErrors();
            return errors != null && ErrorType.TEMPORARILY_UNAVAILABLE.name().equals(errors.getType());
        } catch (Exception e) {
            return false;
        }
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

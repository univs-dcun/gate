package ai.univs.face.shared.feign;

import ai.univs.face.shared.exception.CustomFaceException;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.feign.dto.FeignErrors;
import ai.univs.face.shared.feign.dto.FeignResponseApi;
import ai.univs.face.shared.web.enums.ErrorType;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CommonErrorDecoder implements ErrorDecoder {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Exception decode(String s, Response response) {
        int status = response.status();

        if (status >= 400 && status < 500) {
            FeignResponseApi<?> feignResponse = parseFeignResponse(response);
            FeignErrors feignErrors = feignResponse.getErrors();
            return new CustomFeignException(feignErrors.getCode(), feignErrors.getType(), feignErrors.getMessage());
        }

        // 3xx or 5xx — 하위(ML) 모듈이 자기 오류를 냈다.
        //
        // UG-299: 여기서 남기지 않으면 어떤 상태 코드를 받았는지 알 방법이 사라진다.
        // 이 예외는 GlobalExceptionHandler 가 잡아 ErrorType.INTERNAL_SERVER_ERROR 로
        // 뭉개는데, 그 시점에는 502 였는지 503 이었는지 타임아웃이었는지 구분할 수 없다.
        // 스택트레이스는 남기지 않는다 — 원인은 하위 모듈이고 우리 쪽 호출 스택은 매번 같다.
        log.error("ML 모듈 호출 실패 — method={}, upstreamStatus={}, reason={}",
                s, status, response.reason());

        return new CustomFaceException(ErrorType.INTERNAL_SERVER_ERROR);
    }

    private FeignResponseApi<?> parseFeignResponse(Response response) {
        try {
            return mapper.readValue(response.body().asInputStream(), FeignResponseApi.class);
        } catch (Exception e) {
            // 4xx 인데 본문이 우리가 아는 포맷이 아니다. 하위 모듈이 계약을 바꿨거나 프록시가
            // 끼어든 것이므로 우리 쪽 문제로 올린다 (UG-299: 상태 코드를 함께 남긴다).
            log.error("ML 모듈 오류 응답을 해석하지 못했다 — upstreamStatus={}, {}",
                    response.status(), e.getMessage(), e);
            throw new CustomFaceException(ErrorType.INTERNAL_SERVER_ERROR);
        }
    }
}

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

        // 3xx or 5xx
        return new CustomFaceException(ErrorType.INTERNAL_SERVER_ERROR);
    }

    private FeignResponseApi<?> parseFeignResponse(Response response) {
        try {
            return mapper.readValue(response.body().asInputStream(), FeignResponseApi.class);
        } catch (Exception e) {
            log.error("Parse error for json string: {}", e.getMessage(), e);
            throw new CustomFaceException(ErrorType.INTERNAL_SERVER_ERROR);
        }
    }
}

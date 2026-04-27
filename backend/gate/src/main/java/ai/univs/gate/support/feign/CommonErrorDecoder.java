package ai.univs.gate.support.feign;

import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.feign.dto.FeignErrors;
import ai.univs.gate.support.feign.dto.FeignResponseApi;
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
            return new CustomFeignException(
                    feignErrors.getCode(),
                    feignErrors.getType(),
                    feignErrors.getMessage());
        }

        // 3xx or 5xx
        return new CustomGateException(ErrorType.INTERNAL_SERVER_ERROR);
    }

    private FeignResponseApi<?> parseFeignResponse(Response response) {
        try {
            return mapper.readValue(response.body().asInputStream(), FeignResponseApi.class);
        } catch (Exception e) {
            log.error("Parse error for json string: {}", e.getMessage(), e);
            throw new CustomGateException(ErrorType.INTERNAL_SERVER_ERROR);
        }
    }
}

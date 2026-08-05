package ai.univs.gate.support.feign;

import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
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
        // UG-280: 예전에는 CustomGateException 이었다. 매칭 UseCase 의
        // noRollbackFor 목록에 없는 타입이라 REQUIRES_NEW 트랜잭션이 롤백되면서
        // 매칭 이력 행이 사라졌다. RemoteCallException 은 목록에 들어 있다.
        return new RemoteCallException(status);
    }

    private FeignResponseApi<?> parseFeignResponse(Response response) {
        try {
            return mapper.readValue(response.body().asInputStream(), FeignResponseApi.class);
        } catch (Exception e) {
            log.error("Parse error for json string: {}", e.getMessage(), e);
            // 4xx 인데 본문이 우리 포맷이 아닌 경우다. 원격 호출이 실패한 것은 맞으므로
            // 위와 같은 타입으로 던진다 — 이력 행이 남아야 원인을 추적할 수 있다.
            throw new RemoteCallException(response.status());
        }
    }
}

package ai.univs.gate.support.feign;

import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.feign.dto.FeignErrors;
import ai.univs.gate.support.feign.dto.FeignResponseApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CommonErrorDecoder implements ErrorDecoder {

    private static final int SERVICE_UNAVAILABLE = 503;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Exception decode(String s, Response response) {
        int status = response.status();

        if (status >= 400 && status < 500) {
            FeignResponseApi<?> feignResponse = parseFeignResponse(s, response);
            // 본문이 리터럴 null 이면 readValue 가 null 을 돌려준다 — 파싱은 성공했으므로
            // parseFeignResponse 의 catch 에 걸리지 않는다. 여기서 막지 않으면 다음 줄이 NPE 다.
            FeignErrors feignErrors = feignResponse == null ? null : feignResponse.getErrors();
            if (feignErrors == null) {
                // UG-280 반박 리뷰: 본문이 우리 envelope 모양이긴 하나 errors 가 비어 있는 경우다
                // (예: 프록시·사이드카가 같은 포맷으로 {"success":false,"data":null} 만 반환).
                // 예전에는 여기서 NPE 가 나 noRollbackFor 에 걸리지 않고 이력 행이 사라졌다.
                log.warn("하위 서비스 4xx 응답에 errors 가 없다. methodKey={}, status={}", s, status);
                return new RemoteCallException(status, s, null);
            }
            return new CustomFeignException(
                    feignErrors.getCode(),
                    feignErrors.getType(),
                    feignErrors.getMessage());
        }

        // UG-359: 하위가 503 + TEMPORARILY_UNAVAILABLE 로 "잠시 뒤 다시" 를 알려 온 경우만 따로 가른다.
        // 같은 RemoteCallException 이라 유스케이스의 catch·이력 기록·등록의 "결과 모름" 처리는 그대로 타고,
        // GlobalExceptionHandler 만 503 + PJ-006 + Retry-After 로 내보낸다.
        if (status == SERVICE_UNAVAILABLE && isTemporarilyUnavailable(s, response)) {
            return RemoteCallException.temporarilyUnavailable(status, s);
        }

        // 3xx or 5xx
        // UG-280: 예전에는 CustomGateException 이었다. 매칭 UseCase 의
        // noRollbackFor 목록에 없는 타입이라 REQUIRES_NEW 트랜잭션이 롤백되면서
        // 매칭 이력 행이 사라졌다. RemoteCallException 은 목록에 들어 있다.
        return new RemoteCallException(status, s, null);
    }

    /**
     * 503 본문이 우리 envelope 이고 {@code errors.type} 이 {@code TEMPORARILY_UNAVAILABLE} 인가 (UG-359).
     *
     * <p><b>여기서는 절대 던지지 않는다.</b> 503 은 프록시·로드밸런서·서블릿 컨테이너도 낸다 — 본문이 HTML
     * 이거나 비어 있는 것이 정상이다. 해석에 실패하면 예전과 같은 {@code RemoteCallException(503)} 으로
     * 떨어져야 한다. 4xx 분기의 {@link #parseFeignResponse} 는 실패하면 던지므로 그것을 재사용하지 않는다.
     *
     * <p>유형 이름만 본다 — 코드({@code SWAGGER-006})는 서비스마다 접두어가 달라 계약으로 삼기 어렵다.
     */
    private boolean isTemporarilyUnavailable(String methodKey, Response response) {
        if (response.body() == null) {
            return false;
        }
        try {
            FeignResponseApi<?> feignResponse =
                    mapper.readValue(response.body().asInputStream(), FeignResponseApi.class);
            FeignErrors errors = feignResponse == null ? null : feignResponse.getErrors();
            return errors != null && ErrorType.TEMPORARILY_UNAVAILABLE.name().equals(errors.getType());
        } catch (Exception e) {
            // 우리 포맷이 아닌 503 — 흔한 일이라 DEBUG 로만 남긴다. 실패 자체는 핸들러가 ERROR 로 남긴다.
            log.debug("503 본문을 해석하지 못했다 — 일반 하위 실패로 다룬다. methodKey={}, reason={}",
                    methodKey, e.getMessage());
            return false;
        }
    }

    private FeignResponseApi<?> parseFeignResponse(String methodKey, Response response) {
        try {
            return mapper.readValue(response.body().asInputStream(), FeignResponseApi.class);
        } catch (Exception e) {
            log.error("Parse error for json string: {}", e.getMessage(), e);
            // 4xx 인데 본문이 우리 포맷이 아닌 경우다. 원격 호출이 실패한 것은 맞으므로
            // 위와 같은 타입으로 던진다 — 이력 행이 남아야 원인을 추적할 수 있다.
            throw new RemoteCallException(response.status(), methodKey, e);
        }
    }
}

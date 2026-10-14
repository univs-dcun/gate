package ai.univs.gate.support.feign;

import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.feign.dto.FeignErrors;
import ai.univs.gate.support.feign.dto.FeignResponseApi;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
public class CommonErrorDecoder implements ErrorDecoder {

    private static final int SERVICE_UNAVAILABLE = 503;

    /** 503 본문을 판정하려고 읽는 최대 바이트 (UG-362). {@link #readBodyQuietly} 참고. */
    static final int MAX_503_BODY_BYTES = 64 * 1024;

    /** Spring Cloud LoadBalancer 가 인스턴스 없음을 알릴 때 합성하는 본문의 앞부분 (UG-362). */
    static final String NO_INSTANCE_PREFIX = "Load balancer does not contain an instance for the service ";

    // UG-359 반박 리뷰 L6: 하위 envelope 에 필드가 하나 늘어도(FAIL_ON_UNKNOWN_PROPERTIES 기본 true) 파싱이 실패하지 않게 한다.
    // 실패하면 4xx 의 code 전달과 503 「일시 불가」 신호가 조용히 400 PJ-005 로 떨어진다.
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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
                    feignErrors.getMessage(),
                    feignErrors.getDetails());
        }

        if (status == SERVICE_UNAVAILABLE) {
            // 본문은 한 번만 읽는다 (UG-362). 실제 HTTP 응답의 본문은 스트림이라 두 번째 읽기는 빈 값을 돌려준다 —
            // 판정 둘이 각자 asInputStream() 을 열면 뒤쪽 판정이 조용히 늘 거짓이 된다.
            byte[] body = readBodyQuietly(s, response);

            // UG-359: 하위가 503 + TEMPORARILY_UNAVAILABLE 로 "잠시 뒤 다시" 를 알려 온 경우만 따로 가른다.
            // 같은 RemoteCallException 이라 유스케이스의 catch·이력 기록·등록의 "결과 모름" 처리는 그대로 타고,
            // GlobalExceptionHandler 만 503 + PJ-006 + Retry-After 로 내보낸다.
            if (isTemporarilyUnavailable(s, body)) {
                return RemoteCallException.temporarilyUnavailable(status, s);
            }

            // UG-362: 하위가 아니라 우리 쪽 로드밸런서가 만든 503 이다 — 대상 인스턴스가 없어 요청을 보내지도 않았다.
            // upstreamStatus 는 예전과 같은 503 이라 이력이 바뀌지 않고, 응답만 503 PJ-006 이 된다.
            if (isNoInstance(body)) {
                return RemoteCallException.notDelivered(status, s, null);
            }
        }

        // 3xx or 5xx
        // UG-280: 예전에는 CustomGateException 이었다. 매칭 UseCase 의
        // noRollbackFor 목록에 없는 타입이라 REQUIRES_NEW 트랜잭션이 롤백되면서
        // 매칭 이력 행이 사라졌다. RemoteCallException 은 목록에 들어 있다.
        return new RemoteCallException(status, s, null);
    }

    /**
     * 503 본문을 바이트로 한 번 읽는다 (UG-362). <b>절대 던지지 않는다</b> — 읽지 못하면 빈 배열이다.
     *
     * <p>503 은 프록시·로드밸런서·서블릿 컨테이너도 낸다. 본문이 HTML 이거나 비어 있거나 아예 없는 것이 정상이고,
     * 그때는 예전과 같은 {@code RemoteCallException(503)} 으로 떨어져야 한다. 상한({@link #MAX_503_BODY_BYTES})을
     * 두는 이유도 같다 — 판정에 필요한 것은 앞부분뿐인데 프록시 오류 페이지는 클 수 있다. 상한에서 잘린 JSON 은
     * 파싱에 실패해 일반 하위 실패가 된다. 우리 서비스의 503 본문은 수백 바이트라 닿지 않는다.
     */
    private byte[] readBodyQuietly(String methodKey, Response response) {
        if (response.body() == null) {
            return new byte[0];
        }
        try (InputStream in = response.body().asInputStream()) {
            return in.readNBytes(MAX_503_BODY_BYTES);
        } catch (Exception e) {
            log.debug("503 본문을 읽지 못했다 — 일반 하위 실패로 다룬다. methodKey={}, reason={}",
                    methodKey, e.getMessage());
            return new byte[0];
        }
    }

    /**
     * 503 본문이 우리 envelope 이고 {@code errors.type} 이 {@code TEMPORARILY_UNAVAILABLE} 인가 (UG-359).
     *
     * <p><b>여기서는 절대 던지지 않는다.</b> 해석에 실패하면 거짓이다 — 이유는 {@link #readBodyQuietly} 참고.
     * 4xx 분기의 {@link #parseFeignResponse} 는 실패하면 던지므로 그것을 재사용하지 않는다.
     *
     * <p>유형 이름만 본다 — 코드({@code SWAGGER-006})는 서비스마다 접두어가 달라 계약으로 삼기 어렵다.
     */
    private boolean isTemporarilyUnavailable(String methodKey, byte[] body) {
        if (body.length == 0) {
            return false;
        }
        try {
            FeignResponseApi<?> feignResponse = mapper.readValue(body, FeignResponseApi.class);
            FeignErrors errors = feignResponse == null ? null : feignResponse.getErrors();
            return errors != null && ErrorType.TEMPORARILY_UNAVAILABLE.name().equals(errors.getType());
        } catch (Exception e) {
            // 우리 포맷이 아닌 503 — 흔한 일이라 DEBUG 로만 남긴다. 실패 자체는 핸들러가 ERROR 로 남긴다.
            log.debug("503 본문을 해석하지 못했다 — 일반 하위 실패로 다룬다. methodKey={}, reason={}",
                    methodKey, e.getMessage());
            return false;
        }
    }

    /**
     * Spring Cloud LoadBalancer 가 대상 인스턴스를 찾지 못해 <b>스스로 만든</b> 503 인가 (UG-362).
     *
     * <p>{@code FeignBlockingLoadBalancerClient}(재시도판 {@code RetryableFeignBlockingLoadBalancerClient} 도 같다)는
     * 인스턴스가 없으면 요청을 보내지 않고 상태 503, 평문 본문 {@value #NO_INSTANCE_PREFIX}{@code <serviceId>} 인 응답을
     * 합성한다. 그 응답이 이 디코더로 온다. spring-cloud-openfeign-core 4.1.4·4.3.1·4.3.2 에서 문구가 같음을
     * 확인했다. 라이브러리가 문구를 바꾸면 이 판정이 거짓이 되어 예전처럼 400 {@code PJ-005} 로 떨어진다 — 조용히
     * 503 이 늘어나는 쪽이 아니라 예전 동작으로 돌아가는 쪽이라 안전하다. 테스트가 문구를 고정한다.
     */
    private static boolean isNoInstance(byte[] body) {
        return new String(body, StandardCharsets.UTF_8).startsWith(NO_INSTANCE_PREFIX);
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

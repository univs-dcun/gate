package ai.univs.gate.support.feign;

import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.support.feign.dto.FeignResponseApi;
import feign.FeignException;
import feign.RetryableException;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * Feign 호출을 감싸 <b>응답을 정상적으로 받지 못한 실패</b>를 {@link RemoteCallException} 으로
 * 번역한다 (UG-280).
 *
 * <p>{@link CommonErrorDecoder} 로는 이 경우를 잡을 수 없다. {@code ErrorDecoder} 는 상태 코드 300
 * 이상의 <b>응답이 도착했고 본문 읽기까지 성공했을 때만</b> 불린다. 그 밖의 실패는 Feign 이 직접
 * 예외를 던지는데, 그 예외들은 {@code BusinessException} 계열이 아니므로 매칭 UseCase 의
 * {@code noRollbackFor} 목록에 걸리지 않는다. 결과적으로 <b>UG-280 이 고치려던 증상이 여러 장애
 * 형태에서 그대로 남아 있었다.</b>
 *
 * <p>어떤 실패가 여기로 오는지 (feign-core 13.6.1 기준):
 * <ul>
 *   <li>{@link RetryableException} — 연결 거부·읽기 타임아웃·연결 리셋.
 *       {@code SynchronousMethodHandler.executeAndDecode} 가 {@code IOException} 을 감싼다.
 *       {@code Retryer} 빈이 없어(기본값 {@code NEVER_RETRY}) 그대로 올라온다.
 *   <li>{@link FeignException} — 본문 디코딩 실패. {@code ResponseHandler.handleResponse} 가
 *       {@code FeignException.errorReading()} 으로 감싼다. <b>200 응답에서도 발생한다</b>
 *       (프록시가 본문을 잘라 보내는 경우 등). {@code RetryableException} 은 이것의 하위 타입이다.
 *   <li>인코딩 실패 — {@code EncodeException} 은 {@link FeignException} 하위라 함께 잡힌다.
 * </ul>
 *
 * <p>다만 {@code CommonFeignConfig} 의 인터셉터가 던지는 임의의 {@code RuntimeException} 은
 * {@link FeignException} 이 아니므로 여기서 잡히지 않는다 — 아래 "여기서 잡지 않는 것" 과 같은
 * 취급이다.
 *
 * <p>{@code FeignException} 을 잡아도 우리 디코더가 만든 예외는 삼키지 않는다 —
 * {@code CustomFeignException} 과 {@link RemoteCallException} 은 우리 클래스이고
 * {@code FeignException} 을 상속하지 않는다. 라이브니스 오류 흡수 판정({@code e.getType()})이
 * 깨지지 않는다는 뜻이다.
 *
 * <p><b>여기서 잡지 않는 것.</b> 그 외 {@code RuntimeException} 은 그대로 통과시킨다. 우리 코드의
 * 버그를 "원격 호출 실패" 로 분류하면 원인이 가려지기 때문이다. 다만 그런 예외도 이력 행을
 * 롤백시키므로, 이력 보존을 예외 타입 열거에 의존하지 않는 구조(이력 저장을 별도 트랜잭션으로
 * 분리)가 근본적이다 — 티켓의 옵션 3이며 별도 판단이 필요하다.
 */
@Slf4j
public final class RemoteCalls {

    private RemoteCalls() {
    }

    /**
     * @param operation 로그에 남길 호출 이름 (예: {@code "face.identify"}). 응답이 없는 실패는
     *                  상태 코드가 없으므로, 어느 하위 서비스의 어느 호출이 끊겼는지 알려면
     *                  이 값이 유일한 단서다.
     */
    public static <T> T of(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (FeignException e) {
            // RetryableException 도 FeignException 하위다. 디코딩·인코딩 실패를 함께 덮는다.
            throw new RemoteCallException(RemoteCallException.NO_RESPONSE, operation, e);
        }
    }

    public static void run(String operation, Runnable call) {
        of(operation, () -> {
            call.run();
            return null;
        });
    }

    /**
     * envelope 에서 {@code data} 를 꺼내며 비어 있으면 {@link RemoteCallException} 으로 바꾼다.
     *
     * <p>하위 서비스가 HTTP 200 에 {@code {"success":false,"data":null}} 을 실어 보내면
     * {@code getData()} 가 {@code null} 이고, 곧바로 {@code .getFaceId()} 같은 호출이 NPE 를 낸다.
     * NPE 는 {@code noRollbackFor} 에 걸리지 않아 <b>매칭 이력 행이 사라진다</b> — 오류 본문에
     * {@code errors} 가 없는 경우({@link CommonErrorDecoder})와 같은 결함의 성공 경로 쌍이다.
     */
    public static <T> T data(String operation, Supplier<FeignResponseApi<T>> call) {
        FeignResponseApi<T> response = of(operation, call);
        if (response == null || response.getData() == null) {
            log.error("하위 서비스 응답 본문에 data 가 없다. operation={}, success={}",
                    operation, response != null && response.isSuccess());
            throw new RemoteCallException(RemoteCallException.NO_RESPONSE, operation, null);
        }
        return response.getData();
    }
}

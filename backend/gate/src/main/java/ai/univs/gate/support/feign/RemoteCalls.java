package ai.univs.gate.support.feign;

import ai.univs.gate.shared.exception.RemoteCallException;
import feign.RetryableException;

import java.util.function.Supplier;

/**
 * Feign 호출을 감싸 <b>응답을 받지 못한 실패</b>를 {@link RemoteCallException} 으로 번역한다 (UG-280).
 *
 * <p>{@link CommonErrorDecoder} 로는 이 경우를 잡을 수 없다. {@code ErrorDecoder} 는 상태 코드 300
 * 이상의 <b>응답이 도착했을 때만</b> 불린다. 연결 거부·읽기 타임아웃·연결 리셋은 응답 자체가 없어서
 * Feign 이 {@code IOException} 을 {@link RetryableException} 으로 감싸 던지고, 이 프로젝트에는
 * {@code Retryer} 빈이 없으므로(기본값 {@code NEVER_RETRY}) 그대로 호출자까지 올라온다.
 *
 * <p>그 예외는 {@code BusinessException} 계열이 아니므로 매칭 UseCase 의 {@code noRollbackFor} 목록에
 * 걸리지 않는다. 결과적으로 <b>UG-280 이 고치려던 증상이 가장 흔한 장애 형태에서 그대로 남아 있었다</b> —
 * 하위 서비스가 5xx 를 "응답" 하는 경우보다, 과부하로 응답하지 못하는 경우가 실제로는 더 많다.
 *
 * <p>UseCase 가 아니라 이 지점에서 번역하는 이유는 두 가지다. 애플리케이션 계층에 Feign 타입이
 * 새지 않고, 새 Feign 메서드를 추가할 때 11곳의 {@code noRollbackFor} 를 모두 고치는 대신 이 헬퍼만
 * 쓰면 되기 때문이다.
 */
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
        } catch (RetryableException e) {
            throw new RemoteCallException(RemoteCallException.NO_RESPONSE, operation, e);
        }
    }

    public static void run(String operation, Runnable call) {
        of(operation, () -> {
            call.run();
            return null;
        });
    }
}

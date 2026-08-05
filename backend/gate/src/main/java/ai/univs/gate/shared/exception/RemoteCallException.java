package ai.univs.gate.shared.exception;

import ai.univs.gate.shared.web.enums.ErrorType;
import lombok.Getter;

/**
 * 하위 서비스 호출이 실패했음을 나타낸다 (UG-280).
 *
 * <p>예전에는 이 상황에 {@link CustomGateException}(INTERNAL_SERVER_ERROR) 를 썼다. 그런데 매칭
 * UseCase 들이 {@code noRollbackFor = CustomFeignException.class} 로 선언돼 있어서,
 * {@code CustomGateException} 은 목록에 없으니 {@code REQUIRES_NEW} 트랜잭션이 롤백됐다. 결과적으로
 * face-service·match-server 가 실패할 때 <b>매칭 이력 행이 통째로 사라졌다</b> — 장애를 가장
 * 관측해야 할 상황에서 기록이 없어지는 셈이었다.
 *
 * <p>전용 타입을 만든 이유는 {@code noRollbackFor} 에 {@code CustomGateException} 을 넣는 것으로는
 * 해결할 수 없기 때문이다. 그러면 <b>모든</b> {@code CustomGateException} 에 커밋을 허용하게 되고,
 * {@code FaceFeatureService.createFaceFeature} 처럼 특징점과 이력을 함께 쓰는 경로에서는
 * 반쯤 등록된 특징점이 남는다.
 *
 * <p>두 가지 경로로 만들어진다.
 * <ul>
 *   <li>{@code CommonErrorDecoder} — 하위 서비스가 3xx/5xx 를 <b>응답</b>한 경우.
 *       {@link #upstreamStatus} 에 그 코드가 담긴다.
 *   <li>{@code RemoteCalls} — 연결 거부·타임아웃처럼 <b>응답 자체가 없는</b> 경우.
 *       {@code ErrorDecoder} 는 상태 코드 300 이상의 응답이 도착했을 때만 불리므로 이 경우를
 *       잡지 못한다. Feign 이 던지는 {@code RetryableException} 을 그 지점에서 번역한다.
 *       상태 코드가 없어 {@link #NO_RESPONSE} 가 들어간다.
 * </ul>
 *
 * <p>{@link BusinessException} 을 상속하므로 {@code GlobalExceptionHandler} 가 기존과 동일한
 * {@code PJ-005} 400 응답을 만든다. <b>클라이언트가 보는 계약은 바뀌지 않는다.</b> 오류 코드를
 * 새로 만들면 그 코드로 분기하던 고객 코드가 깨지므로 일부러 유지했다.
 *
 * <p>{@link #upstreamStatus} 와 {@link #operation} 은 로그용이다. 응답에는 넣지 않는다 — 하위
 * 서비스의 상태 코드나 내부 호출 이름을 노출하면 내부 구성이 드러난다.
 */
@Getter
public class RemoteCallException extends BusinessException {

    /** 응답을 받지 못해 상태 코드가 없음 (연결 거부·타임아웃·연결 리셋). */
    public static final int NO_RESPONSE = 0;

    private final int upstreamStatus;

    /** 어느 하위 서비스의 어느 호출이었는지. 알 수 없으면 {@code null}. */
    private final String operation;

    public RemoteCallException(int upstreamStatus) {
        this(upstreamStatus, null, null);
    }

    public RemoteCallException(int upstreamStatus, String operation, Throwable cause) {
        super(ErrorType.INTERNAL_SERVER_ERROR);
        this.upstreamStatus = upstreamStatus;
        this.operation = operation;
        if (cause != null) {
            initCause(cause);
        }
    }

    /** 응답을 받지 못한 실패인지. 로그에서 "죽었다" 와 "오류를 응답했다" 를 가르는 값이다. */
    public boolean isNoResponse() {
        return upstreamStatus == NO_RESPONSE;
    }
}

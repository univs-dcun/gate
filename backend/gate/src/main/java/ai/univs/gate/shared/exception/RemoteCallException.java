package ai.univs.gate.shared.exception;

import ai.univs.gate.shared.web.enums.ErrorType;
import lombok.Getter;

/**
 * 하위 서비스 호출이 실패했음을 나타낸다 (UG-280).
 *
 * <p>예전에는 이 상황에 {@link CustomGateException}(INTERNAL_SERVER_ERROR) 를 썼다. 그런데 매칭
 * UseCase 들이 {@code noRollbackFor = CustomFeignException.class} 로 선언돼 있어서,
 * {@code CustomGateException} 은 목록에 없으니 {@code REQUIRES_NEW} 트랜잭션이 롤백됐다. 결과적으로
 * face-service·match-server 가 5xx 를 낼 때 <b>매칭 이력 행이 통째로 사라졌다</b> — 장애를 가장
 * 관측해야 할 상황에서 기록이 없어지는 셈이었다.
 *
 * <p>전용 타입을 만든 이유는 {@code noRollbackFor} 에 {@code CustomGateException} 을 넣는 것으로는
 * 해결할 수 없기 때문이다. 그러면 <b>모든</b> {@code CustomGateException} 에 커밋을 허용하게 되고,
 * {@code FaceFeatureService.createFaceFeature} 처럼 특징점과 이력을 함께 쓰는 경로에서는
 * 반쯤 등록된 특징점이 남는다.
 *
 * <p>{@link BusinessException} 을 상속하므로 {@code GlobalExceptionHandler} 가 기존과 동일한
 * {@code PJ-005} 400 응답을 만든다. <b>클라이언트가 보는 계약은 바뀌지 않는다.</b> 오류 코드를
 * 새로 만들면 그 코드로 분기하던 고객 코드가 깨지므로 일부러 유지했다.
 *
 * <p>{@code upstreamStatus} 는 로그용이다. 응답에는 넣지 않는다 — 하위 서비스의 상태 코드를
 * 그대로 노출하면 내부 구성이 드러난다.
 */
@Getter
public class RemoteCallException extends BusinessException {

    private final int upstreamStatus;

    public RemoteCallException(int upstreamStatus) {
        super(ErrorType.INTERNAL_SERVER_ERROR);
        this.upstreamStatus = upstreamStatus;
    }
}

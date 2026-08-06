package ai.univs.face.shared.exception;

import ai.univs.face.shared.web.enums.ErrorType;
import lombok.Getter;

/**
 * ML 모듈 호출이 실패했음을 나타낸다 (UG-299).
 *
 * <p>예전에는 {@code CommonErrorDecoder} 가 이 상황에 그냥
 * {@code CustomFaceException(INTERNAL_SERVER_ERROR)} 를 던졌다. 그러면 <b>어떤 상태 코드를
 * 받았는지가 그 자리에서 사라진다</b> — 502 였는지 503 이었는지 리다이렉트였는지 위쪽에서는 알
 * 방법이 없다.
 *
 * <p>UG-299 의 첫 시도는 디코더에서 로그를 남기는 것이었는데, 반박 리뷰가 그게 <b>한 번의 실패에
 * ERROR 두 줄</b>을 만든다는 것을 실측으로 보여 줬다 — 디코더가 한 줄, 그 예외를 받은 핸들러가
 * 스택트레이스와 함께 또 한 줄. ML 매처가 죽어서 초당 50 요청이 실패하면 초당 ERROR 100 줄에
 * 스택트레이스 50 개다. UG-291 이 gate 에서 없앤 이중 기록과 같은 문제다.
 *
 * <p>그래서 gate 의 {@code RemoteCallException} 과 같은 모양으로 바꿨다. 디코더는 상태 코드를
 * <b>예외에 실어서</b> 던지기만 하고, 로그는 핸들러가 한 번만 남긴다.
 *
 * <p>{@link CustomFaceException} 을 상속하므로 기존 {@code catch (CustomFaceException e)} 와
 * {@code @ExceptionHandler(CustomFaceException.class)} 가 그대로 동작한다. 응답도 이전과 같은
 * {@code SWAGGER-005} 400 이다 — 클라이언트가 보는 계약은 바뀌지 않는다.
 *
 * <p>{@link #upstreamStatus} 와 {@link #operation} 은 로그용이다. 응답에는 넣지 않는다 — 하위
 * 모듈의 상태 코드나 내부 호출 이름을 노출하면 내부 구성이 드러난다.
 */
@Getter
public class UpstreamCallException extends CustomFaceException {

    private final int upstreamStatus;

    /** 어느 호출이었는지 (Feign methodKey). 알 수 없으면 {@code null}. */
    private final String operation;

    /** HTTP 상태 문구. 알 수 없으면 {@code null}. */
    private final String reason;

    public UpstreamCallException(int upstreamStatus, String operation, String reason) {
        this(upstreamStatus, operation, reason, null);
    }

    /**
     * {@code cause} 가 있으면 핸들러가 스택트레이스를 함께 남긴다.
     *
     * <p>하위 모듈이 오류를 <b>응답한</b> 경우는 우리 호출 스택이 매번 같아서 정보가 없다. 반면
     * 응답을 해석하지 못한 경우는 우리 쪽 파싱 문제일 수 있어 스택트레이스가 단서가 된다.
     */
    public UpstreamCallException(int upstreamStatus, String operation, String reason, Throwable cause) {
        super(ErrorType.INTERNAL_SERVER_ERROR);
        this.upstreamStatus = upstreamStatus;
        this.operation = operation;
        this.reason = reason;
        if (cause != null) {
            initCause(cause);
        }
    }
}

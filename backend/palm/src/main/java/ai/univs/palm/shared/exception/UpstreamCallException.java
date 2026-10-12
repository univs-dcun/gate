package ai.univs.palm.shared.exception;

import ai.univs.palm.shared.web.enums.ErrorType;
import lombok.Getter;

/**
 * ML 모듈 호출이 실패했음을 나타낸다 (UG-299).
 *
 * <p>예전에는 {@code CommonErrorDecoder} 가 이 상황에 그냥
 * {@code CustomPalmException(INTERNAL_SERVER_ERROR)} 를 던졌다. 그러면 <b>어떤 상태 코드를
 * 받았는지가 그 자리에서 사라진다</b> — 502 였는지 503 이었는지 리다이렉트였는지 위쪽에서는 알
 * 방법이 없다.
 *
 * <p>UG-299 의 첫 시도는 디코더에서 로그를 남기는 것이었는데, 반박 리뷰가 그게 <b>한 번의 실패에
 * ERROR 두 줄</b>을 만든다는 것을 실측으로 보여 줬다 — 디코더가 한 줄, 그 예외를 받은 핸들러가
 * 스택트레이스와 함께 또 한 줄. ML 모듈이 죽어서 초당 50 요청이 실패하면 초당 ERROR 100 줄에
 * 스택트레이스 50 개다. UG-291 이 gate 에서 없앤 이중 기록과 같은 문제다.
 *
 * <p>그래서 gate 의 {@code RemoteCallException} 과 같은 모양으로 바꿨다. 디코더는 상태 코드를
 * <b>예외에 실어서</b> 던지기만 하고, 로그는 핸들러가 한 번만 남긴다.
 *
 * <p>{@link CustomPalmException} 을 상속하므로 기존 {@code catch (CustomPalmException e)} 와
 * {@code @ExceptionHandler(CustomPalmException.class)} 가 그대로 동작한다. 응답 본문도 이전과
 * 같은 {@code SWAGGER-005} 다. 상태 코드는 UG-308 에서 갈렸다 — 오류를 <b>응답한</b> 경우는
 * 400, 응답을 <b>못 받은</b> 경우({@link #NO_RESPONSE})는 500. 둘 다 각 경우의 이전 거동과
 * 같으므로 클라이언트가 보는 계약은 바뀌지 않는다.
 *
 * <p><b>UG-362 에서 세 번째 상태가 생겼다.</b> 요청이 하위에 <b>닿지 않았음이 확실한</b> 경우({@link #notDelivered})는
 * 503 + {@code SWAGGER-006} + {@code Retry-After} 다 — gate 의 디코더가 그것을 {@code PJ-006} 으로 바꿔 클라이언트에게
 * "다시 보내도 된다" 고 알린다. face 와 같은 규칙이다.
 *
 * <p>{@link #upstreamStatus} 와 {@link #operation} 은 로그용이다. 응답에는 넣지 않는다 — 하위
 * 모듈의 상태 코드나 내부 호출 이름을 노출하면 내부 구성이 드러난다.
 */
@Getter
public class UpstreamCallException extends CustomPalmException {

    /**
     * 응답을 아예 받지 못했다는 표시 (UG-308).
     *
     * <p>연결 거부·읽기 타임아웃·연결 리셋에서는 <b>상태 코드라는 것이 없다.</b> 그런 실패까지
     * 실제 상태 코드와 같은 자리에 담으려면 "없음" 을 뜻하는 값이 필요하다. HTTP 상태 코드는
     * 100 미만이 존재하지 않으므로 0 은 실제 값과 절대 충돌하지 않는다.
     *
     * <p>로그에서 이 값이 보이면 <b>응답을 받지 못했다</b>는 뜻이고, 502·503 이 보이면
     * <b>살아 있는데 오류를 응답했다</b>는 뜻이다. 장애 대응에서 갈리는 지점이라 한 값으로
     * 뭉뚱그리지 않는다.
     *
     * <p>"응답을 받지 못했다" 가 곧 "모듈이 죽었다" 는 아니다 (델타 리뷰 지적). 살아 있는
     * 모듈의 200 응답을 우리가 <b>파싱하지 못한</b> 경우도 상태 코드라는 것이 없어 이 값을
     * 쓴다. 둘은 {@code reason} 으로 갈린다 — "연결 실패·타임아웃" 이면 닿지 않은 것이고,
     * "응답 처리 실패" 면 닿았는데 해석을 못 한 것이다. 후자에는 스택트레이스도 함께 남는다. gate 의 {@code RemoteCallException.NO_RESPONSE} 와 같다.
     */
    public static final int NO_RESPONSE = 0;

    private final int upstreamStatus;

    /** 어느 호출이었는지 (Feign methodKey). 알 수 없으면 {@code null}. */
    private final String operation;

    /** HTTP 상태 문구. 알 수 없으면 {@code null}. */
    private final String reason;

    /** 요청이 하위에 닿지 않았음이 확실한지 (UG-362). {@link #notDelivered} 설명 참고. */
    private final boolean notDelivered;

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
        this(upstreamStatus, operation, reason, cause, false);
    }

    private UpstreamCallException(
            int upstreamStatus, String operation, String reason, Throwable cause, boolean notDelivered) {
        super(ErrorType.INTERNAL_SERVER_ERROR);
        this.upstreamStatus = upstreamStatus;
        this.operation = operation;
        this.reason = reason;
        this.notDelivered = notDelivered;
        if (cause != null) {
            initCause(cause);
        }
    }

    /**
     * 요청이 팜 모듈에 <b>닿지 않았다</b> — 모듈은 아무것도 하지 않았다 (UG-362).
     *
     * <p>딱 두 경우다. 연결이 거부됐거나 경로가 없는 경우({@code FeignFailureTranslator} 가 원인 사슬에서
     * {@code ConnectException}·{@code NoRouteToHostException} 을 찾은 경우)와, Spring Cloud LoadBalancer 가 대상
     * 인스턴스를 하나도 갖고 있지 않아 스스로 503 을 만든 경우({@code CommonErrorDecoder})다. 둘 다 요청 바이트가
     * 모듈에 가지 않았으므로 같은 요청을 다시 보내도 안전하다. 그래서 응답만 503 {@code SWAGGER-006} 으로 바꾼다.
     * (지금 팜 모듈 클라이언트는 {@code url} 고정이라 로드밸런서를 거치지 않는다 — 후자는 face 와 규칙을 맞춰 둔
     * 것이다.)
     *
     * <p>읽기·연결 타임아웃({@code SocketTimeoutException})과 연결 리셋은 넣지 않는다 — 연결 타임아웃은 읽기 타임아웃과
     * 같은 타입이라 가를 수 없고, 읽기 타임아웃·리셋은 모듈이 등록을 끝낸 뒤 응답만 잃었을 수 있다. 그런 실패에
     * 재시도를 권하면 중복 등록을 부른다 (gate UG-338).
     *
     * <p>{@code ErrorType} 은 {@code INTERNAL_SERVER_ERROR} 그대로라 이력 사유가 예전과 같다. cause 는 싣지 않는다 —
     * 연결 실패의 스택은 매번 같은 Feign 경로라 정보가 없다. 로그는 핸들러가 예전처럼 ERROR 로 남긴다.
     *
     * @param upstreamStatus 응답이 없으면 {@link #NO_RESPONSE}, 로드밸런서가 만든 응답이면 503 — 예전과 같은 값이다
     */
    public static UpstreamCallException notDelivered(int upstreamStatus, String operation, String reason) {
        return new UpstreamCallException(upstreamStatus, operation, reason, null, true);
    }

    /** 요청이 하위에 닿지 않았음이 확실한 실패인지 (UG-362). */
    public boolean isNotDelivered() {
        return notDelivered;
    }
}

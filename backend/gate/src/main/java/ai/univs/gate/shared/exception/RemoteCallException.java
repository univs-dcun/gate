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
 * 해결할 수 없기 때문이었다. 그러면 <b>모든</b> {@code CustomGateException} 에 커밋을 허용하게 되고,
 * {@code FaceFeatureService.createFaceFeature} 처럼 특징점과 이력을 함께 쓰는 경로에서는
 * 반쯤 등록된 특징점이 남는다.
 *
 * <p><b>UG-293 에서 그 열거 자체가 사라졌다.</b> 이력은 이제 {@code HistoryRecorder} 가 호출자
 * 트랜잭션 밖에서 커밋하므로 어떤 예외에서도 남는다. 이 타입은 여전히 쓸모가 있다 — 하위 서비스
 * 실패를 우리 쪽 실패와 구분하고, {@link #upstreamStatus} 로 원인을 이력에 남긴다 (UG-294).
 *
 * <p>세 가지 경로로 만들어진다.
 * <ul>
 *   <li>{@code CommonErrorDecoder} — 하위 서비스가 3xx/5xx 를 <b>응답</b>했거나, 4xx 인데 본문이
 *       우리 포맷이 아니거나 {@code errors} 가 비어 있는 경우. {@link #upstreamStatus} 에
 *       그 코드가 담긴다.
 *   <li>{@code RemoteCalls.of} — Feign 이 {@code FeignException} 을 던진 경우. 연결 거부·타임아웃
 *       ({@code RetryableException}) 과 본문 디코딩 실패({@code errorReading}) 를 함께 덮는다.
 *       {@code ErrorDecoder} 는 상태 코드 300 이상의 응답이 도착했을 때만 불리므로 이 경우들을
 *       잡지 못한다. 디코딩 실패는 <b>HTTP 200 에서도 일어난다.</b>
 *   <li>{@code RemoteCalls.data} — HTTP 200 인데 envelope 의 {@code data} 가 비어 있는 경우.
 *       예외조차 아니어서 예전에는 곧바로 NPE 가 났다.
 * </ul>
 *
 * <p>뒤의 두 경로는 상태 코드가 없거나 의미가 없어 {@link #NO_RESPONSE} 가 들어간다.
 *
 * <p>{@link BusinessException} 을 상속하므로 {@code GlobalExceptionHandler} 가 기존과 동일한
 * {@code PJ-005} 400 응답을 만든다. 오류 코드로 분기하던 고객 코드는 영향이 없다. 다만 위 세
 * 경로 중 예전에 NPE 로 <b>500</b> 이 나가던 두 가지(4xx + {@code errors} 없음, 200 +
 * {@code data} 없음)는 이제 400 이 된다 — 본문은 같고 상태 코드만 바뀐다.
 *
 * <p><b>400 {@code PJ-005} 가 아닌 변형이 둘 있다.</b> 하위가 "잠시 뒤 다시" 를 알려 온 경우
 * ({@link #temporarilyUnavailable}, UG-359)와 요청이 하위에 <b>닿지도 않은</b> 경우({@link #notDelivered},
 * UG-362)다. 둘 다 응답만 503 + {@code PJ-006} + {@code Retry-After} 로 바뀌고, 유스케이스의 catch·이력·
 * 등록의 "결과 모름" 처리는 다른 하위 실패와 같은 길을 간다. 나머지(읽기 타임아웃·연결 리셋·하위 5xx)는
 * 등록·삭제가 실제로 반영됐는지 알 수 없으므로 재시도를 권하지 않는 400 {@code PJ-005} 그대로다 (UG-338).
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

    /** 요청이 하위에 닿지 않았음이 확실한지 (UG-362). {@link #notDelivered} 설명 참고. */
    private final boolean notDelivered;

    public RemoteCallException(int upstreamStatus) {
        this(upstreamStatus, null, null);
    }

    public RemoteCallException(int upstreamStatus, String operation, Throwable cause) {
        this(ErrorType.INTERNAL_SERVER_ERROR, upstreamStatus, operation, cause, false);
    }

    private RemoteCallException(
            ErrorType errorType, int upstreamStatus, String operation, Throwable cause, boolean notDelivered) {
        super(errorType);
        this.upstreamStatus = upstreamStatus;
        this.operation = operation;
        this.notDelivered = notDelivered;
        if (cause != null) {
            initCause(cause);
        }
    }

    /**
     * 하위 서비스가 "지금은 처리할 여력이 없다" 고 알려 왔다 (UG-359).
     *
     * <p>face 가 자기 풀 고갈(또는 그 아래 match 의 풀 고갈)을 503 + {@code TEMPORARILY_UNAVAILABLE} 로
     * 돌려준 경우다. {@code ErrorType} 만 다르고 나머지는 다른 하위 실패와 같은 길을 간다 — 그래서 새 예외
     * 타입이 아니라 이 타입의 변형으로 둔다.
     * <ul>
     *   <li>유스케이스의 {@code catch (RemoteCallException e)} 가 그대로 잡아 {@code failUpstream(e)} 로 이력을
     *       남긴다. 이력의 {@code failure_type} 은 {@code TEMPORARILY_UNAVAILABLE}, {@code upstream_status} 는 503.
     *   <li>등록 흐름은 여전히 "결과를 모른다" 로 다룬다 — 발급 id 를 남겨 정리 잡(UG-338)이 확인한다. 하위가
     *       트랜잭션을 열지 못해 실제로는 아무것도 안 했을 가능성이 높지만, 그 판단을 여기서 하지 않는다.
     * </ul>
     * 응답만 달라진다 — {@code GlobalExceptionHandler} 가 이 유형이면 503 + {@code PJ-006} + {@code Retry-After}
     * 로 내보낸다.
     */
    public static RemoteCallException temporarilyUnavailable(int upstreamStatus, String operation) {
        return new RemoteCallException(ErrorType.TEMPORARILY_UNAVAILABLE, upstreamStatus, operation, null, false);
    }

    /**
     * 요청이 하위 서비스에 <b>닿지 않았다</b> — 하위는 아무것도 하지 않았다 (UG-362).
     *
     * <p>딱 두 경우다. 본문이 있는 요청의 연결 자체가 거부됐거나 경로가 없는 경우({@code RemoteCalls.of} 가 원인
     * 사슬에서 {@code ConnectException}·{@code NoRouteToHostException} 을 찾았고 요청에 본문이 있는 경우)와, Spring
     * Cloud LoadBalancer 가 대상
     * 인스턴스를 하나도 갖고 있지 않아 스스로 503 을 만든 경우({@code CommonErrorDecoder})다. 후자는 하위가 재기동
     * 중이거나 Eureka 에서 빠진 동안 일어난다 — scaling 에서 match 재기동 중 400 {@code SWAGGER-005} 로 새던 것이
     * 이 경우다. 둘 다 요청 바이트가 하위에 한 번도 가지 않았으므로 등록·삭제가 반영됐을 가능성이 없다 — 같은
     * 요청을 다시 보내도 안전하다. 그래서 응답만 503 + {@code PJ-006} + {@code Retry-After} 로 바꾼다.
     *
     * <p><b>본문 없는 요청(GET·DELETE)의 연결 거부는 넣지 않는다 (UG-362 반박 리뷰).</b> JDK {@code HttpURLConnection}
     * 은 응답 읽기에 실패한 본문 없는 요청을 새 연결로 몰래 한 번 더 보낸다. 하위가 첫 요청을 처리한 뒤 죽으면 호출자는
     * 재전송이 맞은 연결 거부를 본다 — 이미 지워졌는데 "닿지 않았다, 다시 보내라" 가 되는 셈이다. 상세와 실측은
     * {@code RemoteCalls} 클래스 설명에 있다.
     *
     * <p><b>읽기·연결 타임아웃({@code SocketTimeoutException})과 연결 리셋은 넣지 않는다.</b> 연결 타임아웃과 읽기
     * 타임아웃은 같은 예외 타입이라 갈라낼 수 없고, 읽기 타임아웃·리셋은 하위가 등록을 끝낸 뒤 응답만 잃었을 수
     * 있다 — "결과를 모른다" 에 재시도를 권하면 중복 등록을 부른다 (UG-338). {@code UnknownHostException} 도
     * 넣지 않는다. 설정 오류일 때가 많아 기다려도 낫지 않는다.
     *
     * <p>{@code ErrorType} 은 {@code INTERNAL_SERVER_ERROR} 그대로다 — 일부러 새 유형을 만들지 않았다.
     * <ul>
     *   <li>유스케이스의 {@code failUpstream(e)} 가 남기는 이력({@code failure_type}·{@code upstream_status})이 예전과
     *       같다. 이력만 보고 장애를 집계하던 기준이 바뀌지 않는다. <b>단, gate 가 직접 하위(face·palm)에 닿지 못한
     *       경우에만 그렇다.</b> face 가 그 아래 match 에 닿지 못하면 face 가 503 {@code SWAGGER-006}
     *       ({@code TEMPORARILY_UNAVAILABLE})으로 답하고, gate 는 그것을 {@link #temporarilyUnavailable} 경로(UG-359)로
     *       받는다 — 이력의 {@code failure_type} 이 {@code TEMPORARILY_UNAVAILABLE}, {@code upstream_status} 가 503 으로
     *       남고(예전에는 face 가 500 으로 답해 {@code INTERNAL_SERVER_ERROR}·500 이었다. 고객에게도
     *       {@code MatchingHistoryResponseDTO.failureType} 으로 보인다), gate 로그는 WARN, ERROR 는 face 로그에만 남는다.
     *   <li>등록 흐름은 여전히 "결과를 모른다" 로 다루고 발급 id 를 남긴다 — 정리 잡(UG-338)이 하위에 없음을
     *       확인하고 닫는다. 닿지 않았음이 확실해도 그 판단을 이력 쪽으로 끌어들이지 않는다.
     *   <li>{@code LoggingAspect} 와 {@code GlobalExceptionHandler} 는 이것을 ERROR 로 남긴다. 혼잡 신호
     *       ({@link #isTemporarilyUnavailable()})와 달리 이 실패를 기록한 하위 서비스가 없기 때문이다 — 요청이
     *       닿지 않았으니 그쪽 로그는 비어 있다. 여기까지 조용해지면 어디에도 흔적이 남지 않는다.
     * </ul>
     *
     * @param upstreamStatus 응답이 없으면 {@link #NO_RESPONSE}, 로드밸런서가 만든 응답이면 503 — 예전과 같은 값이다
     * @param cause          연결 실패의 원래 예외. 로드밸런서 경우는 {@code null}
     */
    public static RemoteCallException notDelivered(int upstreamStatus, String operation, Throwable cause) {
        return new RemoteCallException(ErrorType.INTERNAL_SERVER_ERROR, upstreamStatus, operation, cause, true);
    }

    /** 요청이 하위에 닿지 않았음이 확실한 실패인지 (UG-362). 응답만 503 {@code PJ-006} 으로 바꾸는 데 쓴다. */
    public boolean isNotDelivered() {
        return notDelivered;
    }

    /** 하위가 "잠시 뒤 다시" 를 알려 온 실패인지 (UG-359). */
    public boolean isTemporarilyUnavailable() {
        return getErrorType() == ErrorType.TEMPORARILY_UNAVAILABLE;
    }

    /** 응답을 받지 못한 실패인지. 로그에서 "죽었다" 와 "오류를 응답했다" 를 가르는 값이다. */
    public boolean isNoResponse() {
        return upstreamStatus == NO_RESPONSE;
    }
}

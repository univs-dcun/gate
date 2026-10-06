package ai.univs.gate.support.webhook;

/**
 * 콘솔 「테스트 전송」 결과 (UG-344).
 *
 * @param result     {@link #SUCCESS}, {@link #HTTP_ERROR}, {@link #TIMEOUT}, {@link #CONNECTION_FAILED},
 *                   {@link #HOST_NOT_FOUND}, {@link #TLS_ERROR}, {@link #TARGET_NOT_ALLOWED}, {@link #TARGET_DENIED_RANGE} 중 하나
 * @param statusCode 수신 서버의 응답 코드. 응답을 받지 못했으면 null
 * @param elapsedMs  보내기 시작부터 결과까지 걸린 시간. 보내지 않았으면({@link #TARGET_NOT_ALLOWED}) null
 * @param eventId    보낸 요청의 {@code eventId} — 수신 측 로그와 대조할 때 쓴다. 보내지 않았으면 null
 */
public record WebhookTestResult(String result, Integer statusCode, Long elapsedMs, String eventId) {

    public static final String SUCCESS = "SUCCESS";
    public static final String HTTP_ERROR = "HTTP_ERROR";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String CONNECTION_FAILED = "CONNECTION_FAILED";
    public static final String HOST_NOT_FOUND = "HOST_NOT_FOUND";
    public static final String TLS_ERROR = "TLS_ERROR";
    public static final String TARGET_NOT_ALLOWED = "TARGET_NOT_ALLOWED";
    /**
     * 사설망을 허용한 설치(온프레미스)에서 {@code gate.webhook.denied-cidrs} 대역에 걸려 보내지 않았다 (UG-348). 클라우드는 어느
     * 목록에 걸렸는지 숨기므로 이 값을 쓰지 않고 {@link #TARGET_NOT_ALLOWED} 로 돌려준다.
     */
    public static final String TARGET_DENIED_RANGE = "TARGET_DENIED_RANGE";

    static WebhookTestResult success(Integer statusCode, long elapsedMs, String eventId) {
        // 2xx 도 어느 코드였는지 화면에 보인다 (「성공 · 200 · 0.4초」)
        return new WebhookTestResult(SUCCESS, statusCode, elapsedMs, eventId);
    }

    static WebhookTestResult failed(String result, Integer statusCode, long elapsedMs, String eventId) {
        return new WebhookTestResult(result, statusCode, elapsedMs, eventId);
    }

    static WebhookTestResult notSent(String result) {
        return new WebhookTestResult(result, null, null, null);
    }
}

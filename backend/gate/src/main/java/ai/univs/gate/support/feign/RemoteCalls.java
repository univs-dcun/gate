package ai.univs.gate.support.feign;

import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.support.feign.dto.FeignResponseApi;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import lombok.extern.slf4j.Slf4j;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
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
 *       이 중 <b>본문이 있는 요청의</b> 연결 거부·경로 없음만 {@link RemoteCallException#notDelivered} 가 된다
 *       (UG-362).
 *       {@code Retryer} 빈이 없어(기본값 {@code NEVER_RETRY}) 그대로 올라온다.
 *   <li>{@link FeignException} — 본문 디코딩 실패. {@code ResponseHandler.handleResponse} 가
 *       {@code FeignException.errorReading()} 으로 감싼다. <b>200 응답에서도 발생한다</b>
 *       (프록시가 본문을 잘라 보내는 경우 등). {@code RetryableException} 은 이것의 하위 타입이다.
 *   <li>인코딩 실패 — {@code EncodeException} 은 {@link FeignException} 하위라 함께 잡힌다.
 * </ul>
 *
 * <p><b>연결 자체를 못 한 경우는 따로 표시한다 (UG-362).</b> {@link RetryableException} 의 원인 사슬에
 * {@code ConnectException}(연결 거부) 또는 {@code NoRouteToHostException}(경로 없음)이 있고 <b>요청에 본문이
 * 있으면</b> 요청이 하위에 닿지 않은 것이 확실하므로 {@link RemoteCallException#notDelivered} 로 던진다 — 응답만
 * 503 {@code PJ-006} 이 된다. 읽기·연결 타임아웃({@code SocketTimeoutException}), 연결 리셋,
 * {@code UnknownHostException} 은 예전 그대로다. 이유는 {@link RemoteCallException#notDelivered} 설명에 있다.
 *
 * <p><b>본문 없는 요청(GET·DELETE)은 연결 거부여도 예전 그대로다 (UG-362 반박 리뷰).</b> Feign 기본 클라이언트
 * ({@code Client.Default}, JDK {@code HttpURLConnection})는 응답을 읽다 실패하면 <b>본문 없는 요청을 새 연결로 한 번
 * 몰래 다시 보낸다</b> ({@code sun.net.www.http.HttpClient.parseHTTP} 의 {@code failedOnce} 재전송). 그래서 하위가
 * 첫 요청을 받아 처리한 뒤 죽으면, 호출자가 보는 예외는 재전송이 맞은 {@code ConnectException: Connection refused}
 * 다 — 연결 거부인데 요청은 이미 닿았다. JDK 21 + feign-core 13.5·13.6.1 로 실측했다 (서버가 요청을 다 읽고 응답
 * 없이 끊은 뒤 리스너를 닫음): GET·DELETE → {@code ConnectException}, 본문 있는 POST·PUT →
 * {@code SocketException: Unexpected end of file from server}. 본문이 있으면 Feign 이 스트리밍 모드
 * ({@code setFixedLengthStreamingMode}/{@code setChunkedStreamingMode})로 보내고, JDK 는 스트리밍 요청을 다시 보내지
 * 않는다. 그러므로 본문이 있는 요청의 {@code ConnectException} 은 첫 연결 시도에서 난 것이고, 요청 바이트는 하위에
 * 한 번도 가지 않았다.
 *
 * <p><b>이 판정은 Feign 의 요청 버퍼링 해제(기본값 {@code disableRequestBuffering = true})에 기댄다.</b>
 * {@code new Client.Default(ssl, verifier, false)} 처럼 버퍼링을 켜면 본문이 있어도 스트리밍이 아니게 되고, 같은
 * 실측에서 <b>버퍼링한 POST·PUT 도 몰래 재전송돼</b> {@code ConnectException} 으로 끝났다 ({@code sun.net.http.retryPost}
 * 기본값 {@code true}). 버퍼링을 켜거나 다른 HTTP 클라이언트(Apache·OkHttp 등 자체 재시도가 있는 것)로 바꾸면 이
 * 판정을 다시 따져야 한다. 빈 본문({@code byte[0]})은 실측상 재전송되지 않았지만, 본문이 실린다는 보장이 없는
 * 경계라 보수적으로 "본문 없음" 으로 친다.
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
            // UG-362: 본문 있는 요청의 연결 거부·경로 없음만 "닿지 않았다" 로 가른다. ErrorType·upstreamStatus 는
            // 아래와 같다 — 이력과 로그 수준은 그대로이고 응답만 503 PJ-006 이 된다. 본문 없는 요청은 JDK 의 몰래
            // 재전송 때문에 연결 거부여도 이미 닿았을 수 있다 (클래스 설명).
            if (e instanceof RetryableException retryable && 연결하지_못했다(retryable) && 본문이_있다(retryable)) {
                throw RemoteCallException.notDelivered(RemoteCallException.NO_RESPONSE, operation, e);
            }
            // RetryableException 도 FeignException 하위다. 디코딩·인코딩 실패를 함께 덮는다.
            throw new RemoteCallException(RemoteCallException.NO_RESPONSE, operation, e);
        }
    }

    /**
     * 원인 사슬에 "연결을 맺지 못했다" 는 예외가 있는가 (UG-362).
     *
     * <p>{@code ConnectException} 의 하위 타입(Apache HttpClient 5 의 {@code HttpHostConnectException} 등)도
     * 포함된다. {@code SocketTimeoutException} 은 {@code ConnectException} 의 하위가 아니므로 걸리지 않는다 —
     * 연결 타임아웃과 읽기 타임아웃을 예외 타입으로 가를 수 없어 일부러 뺐다. 사슬이 순환해도 끝나도록 깊이를
     * 제한한다.
     */
    static boolean 연결하지_못했다(Throwable e) {
        Throwable current = e;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof ConnectException || current instanceof NoRouteToHostException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 요청에 본문이 실려 있는가 — 즉 JDK 가 몰래 다시 보내지 않는 스트리밍 요청인가 (UG-362 반박 리뷰).
     *
     * <p>본문 없는 요청은 {@code HttpURLConnection} 이 응답 읽기 실패 뒤 새 연결로 한 번 더 보내므로, 그 재전송의
     * 연결 거부가 "닿지 않았다" 를 뜻하지 않는다. 요청 정보가 없으면 판단할 수 없으므로 예전 그대로 둔다. 상세는
     * 클래스 설명 참고.
     */
    static boolean 본문이_있다(RetryableException e) {
        Request request = e.request();
        if (request == null) {
            return false;
        }
        byte[] body = request.body();
        return body != null && body.length > 0;
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

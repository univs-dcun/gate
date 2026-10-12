package ai.univs.face.shared.feign;

import ai.univs.face.shared.exception.UpstreamCallException;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.util.Arrays;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

/**
 * Feign 클라이언트 호출이 <b>응답을 받지 못하고</b> 실패했을 때 {@link UpstreamCallException} 으로
 * 번역한다 (UG-308).
 *
 * <p>UG-299 가 만든 {@link CommonErrorDecoder} 경로로는 이 경우를 잡을 수 없다.
 * {@code ErrorDecoder} 는 상태 코드 300 이상의 <b>응답이 도착했을 때만</b> 불리기 때문이다.
 * 연결 거부·읽기 타임아웃·연결 리셋에서는 Feign 이 직접 {@link RetryableException} 을 던지는데,
 * face·palm 어디에도 그것을 잡는 코드가 없어 {@code handleGlobalException} 까지 떨어졌다 —
 * ERROR 에 스택트레이스가 통째로 붙고, <b>어느 호출이 왜 실패했는지 알 수 없었다.</b>
 *
 * <p><b>고치는 것은 진단 정보이지 응답이 아니다.</b> 이 경로는 예전에도 500 이었고 지금도 500
 * 이다 — 델타 리뷰가 상태·본문·헤더를 바이트 단위로 대조해 확인했다(UG-362 에서 연결 거부·경로 없음만
 * 503 이 됐다 — 아래 참고). 초판은 티켓 항목 4를
 * 따라 400 으로 통일하려 했는데, 그러면 gate 의 {@code IdentifyPalmUseCase} 가 4xx 를 정상
 * 결과로 흡수해 모듈 전면 장애가 HTTP 200 "매칭 실패" 로 나간다. 상세는
 * {@code GlobalExceptionHandler.handleUpstreamCallException} 의 분기 주석에 있다.
 *
 * <p>아이러니하게도 그것이 UG-299 가 예로 든 시나리오다 — "ML 매처가 죽어서 초당 50 요청이
 * 실패하면". 매처가 <b>오류를 응답하며</b> 죽으면 새 경로를 타고, <b>정말 죽어서</b> 응답이
 * 없으면 안 탔다.
 *
 * <p>어떤 실패가 여기로 오는지 (feign-core 13.x 기준):
 * <ul>
 *   <li>{@link RetryableException} — 연결 거부·읽기 타임아웃·연결 리셋.
 *       {@code Retryer} 빈이 없어(기본 {@code NEVER_RETRY}) 그대로 올라온다.
 *   <li>{@link FeignException} — 본문 디코딩 실패. <b>HTTP 200 응답에서도 발생한다</b>
 *       (프록시가 본문을 잘라 보내는 경우 등). {@code RetryableException} 은 이것의 하위 타입이다.
 *   <li>인코딩 실패 — {@code EncodeException} 도 {@link FeignException} 하위다.
 * </ul>
 *
 * <p><b>연결 자체를 못 한 경우는 따로 표시한다 (UG-362).</b> {@link RetryableException} 의 원인 사슬에
 * {@code ConnectException}(연결 거부) 또는 {@code NoRouteToHostException}(경로 없음)이 있고 <b>요청에 본문이
 * 있으면</b> 요청이 하위에 닿지 않은 것이 확실하므로 {@link UpstreamCallException#notDelivered} 로 던진다 — 핸들러가
 * 500 대신 503
 * {@code SWAGGER-006} 으로 내보내고, gate 가 그것을 {@code PJ-006} 으로 바꾼다. 5xx 이므로 위의 안전 성질
 * ({@code IdentifyPalmUseCase} 의 4xx 흡수)은 그대로다. 타임아웃·연결 리셋·{@code UnknownHostException} 은 예전
 * 그대로 500 이다. 이유는 {@link UpstreamCallException#notDelivered} 설명에 있다.
 *
 * <p><b>본문 없는 요청(GET·DELETE)은 연결 거부여도 예전 그대로 500 이다 (UG-362 반박 리뷰).</b> Feign 기본
 * 클라이언트({@code Client.Default}, JDK {@code HttpURLConnection})는 응답을 읽다 실패하면 <b>본문 없는 요청을 새
 * 연결로 한 번 몰래 다시 보낸다</b> ({@code sun.net.www.http.HttpClient.parseHTTP} 의 {@code failedOnce} 재전송).
 * 그래서 하위가 첫 요청을 받아 처리한 뒤 죽으면, 호출자가 보는 예외는 재전송이 맞은
 * {@code ConnectException: Connection refused} 다 — 연결 거부인데 요청은 이미 닿았다. 본문 없는 {@code @DeleteMapping}
 * 이라면 삭제가 끝났는데 "닿지 않았으니 다시 보내도 된다" 가 된다. JDK 21 + feign-core 13.5·13.6.1 로 실측했다 (서버가
 * 요청을 다 읽고 응답 없이 끊은 뒤 리스너를 닫음): GET·DELETE → {@code ConnectException}, 본문 있는 POST·PUT →
 * {@code SocketException: Unexpected end of file from server}. 본문이 있으면 Feign 이 스트리밍 모드
 * ({@code setFixedLengthStreamingMode}/{@code setChunkedStreamingMode})로 보내고, JDK 는 스트리밍 요청을 다시 보내지
 * 않는다. 그러므로 본문이 있는 요청의 {@code ConnectException} 은 첫 연결 시도에서 난 것이고, 요청 바이트는 하위에
 * 한 번도 가지 않았다.
 *
 * <p><b>이 판정은 Feign 의 요청 버퍼링 해제(기본값 {@code disableRequestBuffering = true})에 기댄다.</b>
 * {@code new Client.Default(ssl, verifier, false)} 처럼 버퍼링을 켜면 본문이 있어도 스트리밍이 아니게 되고, 같은
 * 실측에서 <b>버퍼링한 POST·PUT 도 몰래 재전송돼</b> {@code ConnectException} 으로 끝났다 ({@code sun.net.http.retryPost}
 * 기본값 {@code true}). 버퍼링을 켜거나 다른 HTTP 클라이언트(Apache·OkHttp 등 자체 재시도가 있는 것)로 바꾸면 이 판정을
 * 다시 따져야 한다. 빈 본문({@code byte[0]})은 실측상 재전송되지 않았지만, 본문이 실린다는 보장이 없는 경계라
 * 보수적으로 "본문 없음" 으로 친다. gate 의 {@code RemoteCalls} 와 같은 기준이다.
 *
 * <p><b>왜 호출처마다 감싸지 않고 빈을 감싸는가.</b> gate 는 {@code RemoteCalls.of} 를 호출처
 * 마다 부른다. face·palm 은 Feign 호출 지점이 UseCase 20여 곳에 흩어져 있어 하나만 빠뜨려도
 * 조용한 회귀가 되고, 새 메서드를 추가할 때 다시 잊기 쉽다. 빈을 감싸면 <b>감싸지 않은 호출이
 * 존재할 수 없다</b> — 커버리지가 규율이 아니라 구조가 된다.
 *
 * <p><b>여기서 잡지 않는 것.</b> {@link FeignException} 이 아닌 {@code RuntimeException} 은
 * 그대로 통과시킨다. 우리 코드의 버그를 "원격 호출 실패" 로 분류하면 원인이 가려진다.
 * {@code CommonErrorDecoder} 가 만드는 {@code CustomFeignException}·
 * {@link UpstreamCallException} 도 {@link FeignException} 을 상속하지 않으므로 삼켜지지 않는다.
 *
 * <p><b>남아 있는 구멍.</b> HTTP 200 인데 envelope 의 {@code data} 가 {@code null} 인 경우는
 * 여기서 잡히지 않는다 — Feign 은 정상 반환했고, NPE 는 그 다음 줄에서 난다. gate 는
 * {@code RemoteCalls.data} 로 덮는다. 반환 타입이 서비스마다 제각각이라(envelope·원시 DTO·
 * {@code List}·{@code void}) 일괄 처리하면 정상 {@code null} 응답까지 막을 위험이 있어
 * 이 티켓 범위 밖으로 뒀다.
 */
@Component
public class FeignFailureTranslator implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> target = 감쌀_인터페이스(bean);
        if (target == null) {
            return bean;
        }

        // 대상 하나만 넘기지 않고 원본의 인터페이스를 <b>전부</b> 유지한다 (반박 리뷰 지적).
        // 하나만 넘기면 Feign 이 붙였을 수 있는 다른 인터페이스(Advised·SpringProxy 등)가
        // 소리 없이 사라진다. 오늘은 LoggingAspect 포인트컷이 @RestController 한정이라
        // 실제 유실이 없지만, feign 빈에 어드바이스가 붙는 순간 조용히 깨진다.
        Class<?>[] interfaces = bean.getClass().getInterfaces();

        return Proxy.newProxyInstance(
                target.getClassLoader(),
                interfaces,
                (proxy, method, args) -> 번역해서_호출한다(bean, target, proxy, method, args));
    }

    /**
     * 이 빈이 {@code @FeignClient} 인터페이스 구현인가. 아니면 {@code null}.
     *
     * <p>Feign 이 만드는 빈은 JDK 동적 프록시라 인터페이스만 갖는다. 그 인터페이스 중
     * {@code @FeignClient} 가 붙은 것을 찾는다.
     */
    private static Class<?> 감쌀_인터페이스(Object bean) {
        for (Class<?> candidate : bean.getClass().getInterfaces()) {
            if (AnnotatedElementUtils.hasAnnotation(candidate, FeignClient.class)) {
                return candidate;
            }
        }
        return null;
    }

    private static Object 번역해서_호출한다(
            Object bean, Class<?> target, Object proxy,
            java.lang.reflect.Method method, Object[] args) throws Throwable {
        // Object.equals 는 프록시 자신을 기준으로 답한다 (반박 리뷰 지적). 그대로 위임하면
        // Feign 의 FeignInvocationHandler.equals 가 인자에서 InvocationHandler 를 꺼내
        // 비교하는데 우리 람다는 그 타입이 아니라 bean.equals(bean) 조차 false 가 된다 —
        // Object.equals 계약의 반사성 위반이다.
        //
        // 파라미터 타입까지 본다 (델타 리뷰 지적). 이름과 인자 개수만 보면 Feign 인터페이스에
        // 있는 동명의 <b>비즈니스 메서드</b>(예: boolean equals(String))까지 가로채 항상
        // false 를 돌려준다 — 예외도 로그도 없는 조용한 오답이다. 지금 세 인터페이스에는
        // 그런 메서드가 없지만, 생기는 순간 원인을 찾기 어려운 종류의 버그다.
        if (Object_equals_인가(method)) {
            return proxy == args[0];
        }

        try {
            return method.invoke(bean, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof FeignException feignException) {
                // operation 은 상태 코드가 없는 실패에서 '어느 호출이 끊겼는가' 를 알려주는
                // 유일한 단서다. Feign 의 methodKey 와 같은 모양으로 맞춘다.
                //
                // cause 는 <b>연결 실패가 아닐 때만</b> 싣는다. 핸들러가 cause 를 slf4j 의
                // 마지막 인자로 넘기므로 그때만 스택트레이스가 붙는데, 연결 거부·타임아웃의
                // 스택은 매번 같은 Feign 호출 경로라 정보가 없다. UpstreamCallException 이
                // 이미 세우고 있는 원칙("오류를 응답한 경우는 스택이 매번 같아 정보가 없다")을
                // 그대로 적용한 것이다.
                //
                // 반박 리뷰가 실측으로 지적한 부분이다 — 초판은 무조건 cause 를 실어서
                // 스택 프레임이 213 → 223 으로 오히려 늘었고, 그러면서 커밋 메시지는
                // "90여 줄 스택트레이스를 없앴다" 고 적고 있었다. 반면 디코딩 실패는 우리 쪽
                // 파싱 문제일 수 있어 스택이 단서가 되므로 그대로 싣는다.
                boolean 응답을_못_받았다 = feignException instanceof RetryableException;
                String operation = "%s#%s".formatted(target.getSimpleName(), method.getName());

                // UG-362: 본문 있는 요청의 연결 거부·경로 없음만 "닿지 않았다" 로 가른다. 상태·사유·cause 는 아래와
                // 같다 — 이력과 로그는 그대로이고 응답만 503 SWAGGER-006 이 된다. 본문 없는 요청은 JDK 의 몰래 재전송
                // 때문에 연결 거부여도 이미 닿았을 수 있다 (클래스 설명).
                if (feignException instanceof RetryableException retryable
                        && 연결하지_못했다(retryable) && 본문이_있다(retryable)) {
                    throw UpstreamCallException.notDelivered(
                            UpstreamCallException.NO_RESPONSE, operation, 원인_요약(feignException));
                }

                throw new UpstreamCallException(
                        UpstreamCallException.NO_RESPONSE,
                        operation,
                        원인_요약(feignException),
                        응답을_못_받았다 ? null : feignException);
            }
            throw cause;
        }
    }


    /**
     * {@code Object.equals(Object)} 인가. 동명의 비즈니스 메서드는 제외한다.
     *
     * <p>이름·인자 개수·파라미터 타입을 따로 검사하지 않고 <b>시그니처를 통째로</b> 비교한다.
     * 세 조건을 나열하면 그중 하나가 느슨해져도(예: {@code == 1} 이 {@code >= 1} 로) 컴파일도
     * 테스트도 통과한다 — 실제로 변이 심기에서 그 형태가 살아남았다. 시그니처 하나로 묶으면
     * 그런 부분적 완화가 표현되지 않는다. 인자 배열이 정확히 1칸임도 여기서 보장되므로
     * 호출부의 {@code args[0]} 이 안전하다.
     */
    private static boolean Object_equals_인가(java.lang.reflect.Method method) {
        return "equals".equals(method.getName())
                && Arrays.equals(method.getParameterTypes(), new Class<?>[] {Object.class});
    }

    /**
     * 원인 사슬에 "연결을 맺지 못했다" 는 예외가 있는가 (UG-362). gate 의 {@code RemoteCalls} 와 같은 기준이다.
     *
     * <p>{@code ConnectException} 의 하위 타입(Apache HttpClient 5 의 {@code HttpHostConnectException} 등)도
     * 포함된다. {@code SocketTimeoutException} 은 {@code ConnectException} 의 하위가 아니므로 걸리지 않는다 — 연결
     * 타임아웃과 읽기 타임아웃을 예외 타입으로 가를 수 없어 일부러 뺐다. 사슬이 순환해도 끝나도록 깊이를 제한한다.
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
     * 클래스 설명 참고. gate 의 {@code RemoteCalls} 와 같은 기준이다.
     */
    static boolean 본문이_있다(RetryableException e) {
        Request request = e.request();
        if (request == null) {
            return false;
        }
        byte[] body = request.body();
        return body != null && body.length > 0;
    }

    /**
     * 로그 한 줄에 들어갈 짧은 사유.
     *
     * <p>{@code FeignException.getMessage()} 는 요청 URL·본문 일부까지 담아 길고, 그 안에
     * 이미지 바이트나 descriptor 가 섞일 수 있다. 예외 타입과 원인 클래스만 남긴다 —
     * 전체 내용은 어차피 {@code cause} 스택트레이스에 있다.
     */
    private static String 원인_요약(FeignException e) {
        String 종류 = e instanceof RetryableException ? "연결 실패·타임아웃" : "응답 처리 실패";
        Throwable root = e.getCause();
        return root == null ? 종류 : "%s (%s)".formatted(종류, root.getClass().getSimpleName());
    }
}

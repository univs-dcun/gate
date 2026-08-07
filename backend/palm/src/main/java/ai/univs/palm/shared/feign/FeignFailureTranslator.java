package ai.univs.palm.shared.feign;

import ai.univs.palm.shared.exception.UpstreamCallException;
import feign.FeignException;
import feign.RetryableException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
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
 * ERROR 에 90여 줄 스택트레이스가 붙고, 클라이언트는 다른 경로와 달리 <b>500</b> 을 받고,
 * 어느 호출이 왜 실패했는지 알 수 없었다.
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
        if ("equals".equals(method.getName()) && args != null && args.length == 1) {
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

                throw new UpstreamCallException(
                        UpstreamCallException.NO_RESPONSE,
                        "%s#%s".formatted(target.getSimpleName(), method.getName()),
                        원인_요약(feignException),
                        응답을_못_받았다 ? null : feignException);
            }
            throw cause;
        }
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

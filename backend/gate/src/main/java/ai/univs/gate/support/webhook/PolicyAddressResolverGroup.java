package ai.univs.gate.support.webhook;

import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.InetNameResolver;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * 웹훅 연결에 쓰는 주소를 {@link WebhookTargetPolicy} 로 거르는 리졸버 (UG-111).
 *
 * <p>보내기 직전의 검사만으로는 DNS 리바인딩을 못 막는다. 검사할 때는 공인 주소를, 실제 연결할
 * 때는 내부 주소를 돌려주는 DNS 서버가 있으면 검사가 통과한 뒤 내부로 연결된다. 그래서 연결에
 * 실제로 쓰이는 이 해석 단계에서 한 번 더 거른다.
 *
 * <p>JDK 리졸버를 쓰므로 해석이 이벤트 루프 스레드를 잠깐 막는다 — netty 의
 * {@code DefaultNameResolver} 와 같은 방식이다. 그래서 {@link WebhookService} 는 웹훅 전용
 * 이벤트 루프를 따로 둔다. 다른 WebClient 에 영향이 가지 않는다.
 *
 * <p>IP 리터럴 URL 은 reactor-netty 가 해석을 건너뛰어 여기 오지 않는다. 그 경우는
 * {@link WebhookTargetPolicy#validate} 가 보내기 직전에 막는다.
 */
final class PolicyAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {

    private final WebhookTargetPolicy policy;

    PolicyAddressResolverGroup(WebhookTargetPolicy policy) {
        this.policy = policy;
    }

    @Override
    protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
        return new PolicyNameResolver(executor, policy).asAddressResolver();
    }

    private static final class PolicyNameResolver extends InetNameResolver {

        private final WebhookTargetPolicy policy;

        PolicyNameResolver(EventExecutor executor, WebhookTargetPolicy policy) {
            super(executor);
            this.policy = policy;
        }

        @Override
        protected void doResolve(String host, Promise<InetAddress> promise) {
            try {
                promise.setSuccess(policy.resolveAllowed(host).getFirst());
            } catch (Exception e) {
                promise.setFailure(e);
            }
        }

        @Override
        protected void doResolveAll(String host, Promise<List<InetAddress>> promise) {
            try {
                promise.setSuccess(policy.resolveAllowed(host));
            } catch (Exception e) {
                promise.setFailure(e);
            }
        }
    }
}

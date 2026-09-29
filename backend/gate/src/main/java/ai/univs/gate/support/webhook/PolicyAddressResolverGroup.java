package ai.univs.gate.support.webhook;

import io.netty.resolver.AbstractAddressResolver;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * 비동기 DNS 결과를 {@link WebhookTargetPolicy} 로 거르는 리졸버 (UG-111).
 *
 * <p><b>왜 reactor-netty 의 {@code resolvedAddressesSelector} 가 아닌가</b> (2차 반박 리뷰 B1).
 * selector 에서 예외를 던지면, DNS 결과가 동기로 끝난 경우(hosts 파일·DNS 캐시 적중·IP 리터럴)
 * reactor-netty 가 이미 등록한 채널을 닫지 않는다. 막힌 주소로 보낼 때마다 소켓 fd 가 하나씩
 * 재기동 전까지 남았다 — DNS 리바인딩 도메인 하나로 원격에서 gate 를 멈출 수 있었다. 해석
 * Future 를 실패시키면 reactor-netty 는 동기·비동기 두 경로 모두에서 채널을 닫는다.
 *
 * <p>위임 대상은 netty 의 비동기 {@code DnsAddressResolverGroup} 이다 — 이벤트 루프를 막지 않는다.
 * hosts 파일도 읽는다. IP 리터럴은 이미 풀린 주소라 여기 오지 않는다 —
 * {@link WebhookTargetPolicy#checkWithoutLookup} 가 먼저 막는다.
 */
final class PolicyAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {

    private final AddressResolverGroup<InetSocketAddress> delegate;
    private final WebhookTargetPolicy policy;

    PolicyAddressResolverGroup(AddressResolverGroup<InetSocketAddress> delegate, WebhookTargetPolicy policy) {
        this.delegate = delegate;
        this.policy = policy;
    }

    @Override
    protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
        return new Filtering(executor, delegate.getResolver(executor), policy);
    }

    @Override
    public void close() {
        super.close();
        delegate.close();
    }

    private static final class Filtering extends AbstractAddressResolver<InetSocketAddress> {

        private final AddressResolver<InetSocketAddress> inner;
        private final WebhookTargetPolicy policy;

        Filtering(EventExecutor executor, AddressResolver<InetSocketAddress> inner, WebhookTargetPolicy policy) {
            super(executor, InetSocketAddress.class);
            this.inner = inner;
            this.policy = policy;
        }

        @Override
        protected boolean doIsResolved(InetSocketAddress address) {
            return !address.isUnresolved();
        }

        @Override
        protected void doResolve(InetSocketAddress unresolved, Promise<InetSocketAddress> promise) {
            inner.resolve(unresolved).addListener((Future<InetSocketAddress> f) -> {
                if (!f.isSuccess()) {
                    promise.tryFailure(f.cause());
                } else if (!policy.isAllowed(f.getNow().getAddress())) {
                    promise.tryFailure(new WebhookTargetPolicy.TargetNotAllowedException(unresolved.getHostString()));
                } else {
                    promise.trySuccess(f.getNow());
                }
            });
        }

        @Override
        protected void doResolveAll(InetSocketAddress unresolved, Promise<List<InetSocketAddress>> promise) {
            inner.resolveAll(unresolved).addListener((Future<List<InetSocketAddress>> f) -> {
                if (!f.isSuccess()) {
                    promise.tryFailure(f.cause());
                    return;
                }
                // 하나라도 막힌 주소가 섞이면 거절한다 — 공인·내부 주소를 함께 돌려주는 레코드 우회 차단
                for (InetSocketAddress address : f.getNow()) {
                    if (address.getAddress() == null || !policy.isAllowed(address.getAddress())) {
                        promise.tryFailure(new WebhookTargetPolicy.TargetNotAllowedException(unresolved.getHostString()));
                        return;
                    }
                }
                promise.trySuccess(f.getNow());
            });
        }

        @Override
        public void close() {
            // inner 는 위임 그룹이 소유한다 — 그룹을 닫을 때 함께 닫힌다.
        }
    }
}

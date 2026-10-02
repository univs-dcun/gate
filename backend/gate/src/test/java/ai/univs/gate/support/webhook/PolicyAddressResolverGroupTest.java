package ai.univs.gate.support.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.resolver.AbstractAddressResolver;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 연결 단계 리졸버 (UG-111). DNS 가 돌려준 주소를 흉내 내어 판정만 본다.
 */
@DisplayName("UG-111: 연결 단계에서 DNS 결과를 거른다")
class PolicyAddressResolverGroupTest {

    private static final WebhookTargetPolicy POLICY = new WebhookTargetPolicy(new WebhookProperties(
            false, Duration.ofSeconds(3), Duration.ofSeconds(5), 3, Duration.ofSeconds(1), 50, 500, 1000, List.of()));

    /** 어떤 이름이든 주어진 주소들로 푸는 가짜 DNS. */
    private static AddressResolverGroup<InetSocketAddress> dns(String... ips) {
        return new AddressResolverGroup<>() {
            @Override
            protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
                return new AbstractAddressResolver<>(executor, InetSocketAddress.class) {
                    @Override
                    protected boolean doIsResolved(InetSocketAddress address) {
                        return !address.isUnresolved();
                    }

                    @Override
                    protected void doResolve(InetSocketAddress unresolved, Promise<InetSocketAddress> promise) throws Exception {
                        promise.setSuccess(new InetSocketAddress(InetAddress.getByName(ips[0]), unresolved.getPort()));
                    }

                    @Override
                    protected void doResolveAll(InetSocketAddress unresolved, Promise<List<InetSocketAddress>> promise)
                            throws Exception {
                        List<InetSocketAddress> all = new java.util.ArrayList<>();
                        for (String ip : ips) all.add(new InetSocketAddress(InetAddress.getByName(ip), unresolved.getPort()));
                        promise.setSuccess(all);
                    }
                };
            }
        };
    }

    private static Future<List<InetSocketAddress>> resolveAll(AddressResolverGroup<InetSocketAddress> dns) {
        return resolveAll(dns, POLICY);
    }

    private static Future<List<InetSocketAddress>> resolveAll(
            AddressResolverGroup<InetSocketAddress> dns, WebhookTargetPolicy policy) {
        var group = new PolicyAddressResolverGroup(dns, policy);
        return group.getResolver(ImmediateEventExecutor.INSTANCE)
                .resolveAll(InetSocketAddress.createUnresolved("receiver.example.com", 443));
    }

    @Test
    @DisplayName("UG-348: 이름이 차단 대역의 공인 주소로 풀리면 연결 단계에서 실패시킨다 — 저장 뒤 DNS 가 바뀐 경우")
    void 차단_대역() {
        var denying = new WebhookTargetPolicy(new WebhookProperties(
                false, Duration.ofSeconds(3), Duration.ofSeconds(5), 3, Duration.ofSeconds(1), 50, 500, 1000,
                List.of("203.0.113.0/24")));

        Future<List<InetSocketAddress>> f = resolveAll(dns("8.8.8.8", "203.0.113.7"), denying);

        assertThat(f.isSuccess()).isFalse();
        assertThat(f.cause()).isInstanceOf(WebhookTargetPolicy.TargetNotAllowedException.class);
    }

    @Test
    @DisplayName("UG-348: 사설망 허용 설치에서 차단 대역으로 풀리면 사유를 싣는다 — 클라우드는 싣지 않는다")
    void 차단_대역_사유() {
        var onprem = new WebhookTargetPolicy(new WebhookProperties(
                true, Duration.ofSeconds(3), Duration.ofSeconds(5), 3, Duration.ofSeconds(1), 50, 500, 1000,
                List.of("10.20.0.0/16")));
        var cloud = new WebhookTargetPolicy(new WebhookProperties(
                false, Duration.ofSeconds(3), Duration.ofSeconds(5), 3, Duration.ofSeconds(1), 50, 500, 1000,
                List.of("10.20.0.0/16")));

        var onpremCause = (WebhookTargetPolicy.TargetNotAllowedException) resolveAll(dns("10.20.3.4"), onprem).cause();
        var loopbackCause = (WebhookTargetPolicy.TargetNotAllowedException) resolveAll(dns("127.0.0.1"), onprem).cause();
        var cloudCause = (WebhookTargetPolicy.TargetNotAllowedException) resolveAll(dns("10.20.3.4"), cloud).cause();

        assertThat(onpremCause.deniedRange).isTrue();
        assertThat(loopbackCause.deniedRange).as("루프백은 차단 대역이 아니다").isFalse();
        assertThat(cloudCause.deniedRange).isFalse();
    }

    @Test
    @DisplayName("공인 주소만 나오면 그대로 연결한다")
    void 공인() {
        Future<List<InetSocketAddress>> f = resolveAll(dns("8.8.8.8", "1.1.1.1"));
        assertThat(f.isSuccess()).isTrue();
        assertThat(f.getNow()).hasSize(2);
    }

    @Test
    @DisplayName("공인과 내부 주소가 섞이면 해석 자체를 실패시킨다 — 첫 주소만 보면 뚫린다")
    void 섞인_레코드() {
        Future<List<InetSocketAddress>> f = resolveAll(dns("8.8.8.8", "10.0.0.1"));
        assertThat(f.isSuccess()).isFalse();
        assertThat(f.cause()).isInstanceOf(WebhookTargetPolicy.TargetNotAllowedException.class);
    }

    @Test
    @DisplayName("리바인딩: 이름이 루프백·메타데이터로 풀리면 실패시킨다 (resolve·resolveAll 둘 다)")
    void 리바인딩() {
        assertThat(resolveAll(dns("127.0.0.1")).isSuccess()).isFalse();
        var group = new PolicyAddressResolverGroup(dns("169.254.169.254"), POLICY);
        Future<InetSocketAddress> one = group.getResolver(ImmediateEventExecutor.INSTANCE)
                .resolve(InetSocketAddress.createUnresolved("metadata.example.com", 80));
        assertThat(one.isSuccess()).isFalse();
        assertThat(one.cause()).isInstanceOf(WebhookTargetPolicy.TargetNotAllowedException.class);
    }
}

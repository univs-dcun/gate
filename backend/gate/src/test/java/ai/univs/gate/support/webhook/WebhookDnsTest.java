package ai.univs.gate.support.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.resolver.dns.SingletonDnsServerAddressStreamProvider;
import io.netty.util.concurrent.Future;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 실제 netty 비동기 DNS 로 푼 결과를 거르고, 실패 종류를 재시도 판정에 맞게 가른다 (UG-111, 3차 반박 리뷰 W1).
 *
 * <p>로컬 UDP 에 가짜 DNS 를 띄운다. 이름의 앞머리로 응답을 정한다 — hosts 파일에 없는 이름이라
 * 실제 DNS 질의 경로를 탄다.
 * <ul>
 *   <li>{@code public…} → 8.8.8.8, {@code rebind…} → 127.0.0.1, {@code mixed…} → 8.8.8.8 + 10.0.0.1
 *   <li>{@code nx…} → NXDOMAIN, {@code servfail…} → SERVFAIL
 * </ul>
 */
@DisplayName("UG-111: 비동기 DNS 결과 거르기와 DNS 실패 재시도 판정")
class WebhookDnsTest {

    private static final WebhookTargetPolicy POLICY = new WebhookTargetPolicy(new WebhookProperties(
            false, Duration.ofSeconds(3), Duration.ofSeconds(5), 3, Duration.ofSeconds(1), 50, 500, 1000));

    private DatagramSocket dns;
    private NioEventLoopGroup loops;
    private PolicyAddressResolverGroup resolvers;

    @BeforeEach
    void start() throws Exception {
        dns = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        Thread responder = new Thread(this::answer, "fake-dns");
        responder.setDaemon(true);
        responder.start();
        loops = new NioEventLoopGroup(1);
        DnsNameResolverBuilder builder = new DnsNameResolverBuilder()
                .datagramChannelType(NioDatagramChannel.class)
                .nameServerProvider(new SingletonDnsServerAddressStreamProvider(
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), dns.getLocalPort())))
                .searchDomains(List.of())
                .queryTimeoutMillis(2000)
                .maxQueriesPerResolve(1);
        resolvers = new PolicyAddressResolverGroup(new DnsAddressResolverGroup(builder), POLICY);
    }

    @AfterEach
    void stop() {
        resolvers.close();
        loops.shutdownGracefully(0, 0, java.util.concurrent.TimeUnit.SECONDS);
        dns.close();
    }

    private Future<List<InetSocketAddress>> resolve(String host) {
        EventLoop loop = loops.next();
        return resolvers.getResolver(loop)
                .resolveAll(InetSocketAddress.createUnresolved(host, 443))
                .awaitUninterruptibly();
    }

    @Test
    @DisplayName("공인 주소로 풀리면 통과한다 — hosts 파일이 아니라 실제 DNS 질의 경로")
    void 공인() {
        Future<List<InetSocketAddress>> f = resolve("public.webhook.test");
        assertThat(f.isSuccess()).as(String.valueOf(f.cause())).isTrue();
        assertThat(f.getNow()).extracting(a -> a.getAddress().getHostAddress()).containsExactly("8.8.8.8");
    }

    @Test
    @DisplayName("루프백으로 풀리거나(리바인딩) 내부 주소가 섞이면 막고, 재시도하지 않는다")
    void 리바인딩과_섞인_레코드() {
        for (String host : List.of("rebind.webhook.test", "mixed.webhook.test")) {
            Future<List<InetSocketAddress>> f = resolve(host);
            assertThat(f.isSuccess()).as(host).isFalse();
            assertThat(f.cause()).as(host).isInstanceOf(WebhookTargetPolicy.TargetNotAllowedException.class);
            assertThat(WebhookService.isRetryable(f.cause())).as(host).isFalse();
            assertThat(WebhookService.describe(new RuntimeException(f.cause()))).contains("TARGET_NOT_ALLOWED");
        }
    }

    @Test
    @DisplayName("NXDOMAIN(없는 호스트)은 재시도하지 않는다")
    void NXDOMAIN() {
        Future<List<InetSocketAddress>> f = resolve("nx.webhook.test");
        assertThat(f.isSuccess()).isFalse();
        assertThat(WebhookService.isRetryable(new RuntimeException(f.cause()))).isFalse();
        assertThat(WebhookService.describe(new RuntimeException(f.cause()))).contains("NXDomain");   // netty DnsResponseCode.toString()
    }

    @Test
    @DisplayName("SERVFAIL 은 일시적일 수 있어 재시도한다 — UnknownHostException 이지만 NXDOMAIN 이 아니다")
    void SERVFAIL() {
        Future<List<InetSocketAddress>> f = resolve("servfail.webhook.test");
        assertThat(f.isSuccess()).isFalse();
        assertThat(f.cause()).isInstanceOf(java.net.UnknownHostException.class);
        assertThat(WebhookService.isRetryable(new RuntimeException(f.cause()))).isTrue();
    }

    /** 질문을 그대로 되돌리고, 이름에 따라 RCODE·A 레코드를 붙인다. AAAA 에는 빈 답을 준다. */
    private void answer() {
        byte[] buf = new byte[1500];
        while (!dns.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                dns.receive(packet);
                byte[] q = Arrays.copyOf(packet.getData(), packet.getLength());
                int i = 12;
                StringBuilder name = new StringBuilder();
                while (q[i] != 0) {
                    int len = q[i] & 0xff;
                    name.append(new String(q, i + 1, len)).append('.');
                    i += len + 1;
                }
                int qtype = ((q[i + 1] & 0xff) << 8) | (q[i + 2] & 0xff);
                int questionEnd = i + 5;
                String n = name.toString().toLowerCase(Locale.ROOT);

                int rcode = n.startsWith("nx") ? 3 : n.startsWith("servfail") ? 2 : 0;
                List<byte[]> answers = List.of();
                if (qtype == 1) {
                    if (n.startsWith("public")) answers = List.of(new byte[] {8, 8, 8, 8});
                    if (n.startsWith("rebind")) answers = List.of(new byte[] {127, 0, 0, 1});
                    if (n.startsWith("mixed")) answers = List.of(new byte[] {8, 8, 8, 8}, new byte[] {10, 0, 0, 1});
                }

                ByteArrayOutputStream r = new ByteArrayOutputStream();
                r.write(q[0]);
                r.write(q[1]);
                r.write(0x84 | (q[2] & 0x01));   // QR, AA, RD 유지
                r.write(0x80 | rcode);            // RA + RCODE
                r.write(0); r.write(1);           // QDCOUNT
                r.write(0); r.write(answers.size());
                r.write(0); r.write(0); r.write(0); r.write(0);
                r.write(q, 12, questionEnd - 12);
                for (byte[] a : answers) {
                    r.write(0xc0); r.write(12);                 // 질문의 이름을 가리키는 포인터
                    r.write(0); r.write(1); r.write(0); r.write(1);   // A, IN
                    r.write(0); r.write(0); r.write(0); r.write(60);  // TTL
                    r.write(0); r.write(4);
                    r.write(a);
                }
                byte[] response = r.toByteArray();
                dns.send(new DatagramPacket(response, response.length, packet.getSocketAddress()));
            } catch (Exception e) {
                if (dns.isClosed()) return;
            }
        }
    }
}

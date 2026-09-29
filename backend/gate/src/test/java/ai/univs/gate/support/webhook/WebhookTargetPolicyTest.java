package ai.univs.gate.support.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 웹훅 대상 주소 정책 (UG-111).
 *
 * <p>IP 리터럴만 쓴다 — DNS 를 타지 않아 CI 네트워크와 무관하게 결정적이다.
 */
@DisplayName("UG-111: 웹훅 대상 주소 정책")
class WebhookTargetPolicyTest {

    private static WebhookProperties props(boolean allowPrivate) {
        return new WebhookProperties(
                allowPrivate, Duration.ofSeconds(3), Duration.ofSeconds(5), 3, Duration.ofSeconds(1), 50, 500, 1000);
    }

    private static WebhookTargetPolicy policy(boolean allowPrivate) {
        return new WebhookTargetPolicy(props(allowPrivate));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "https://8.8.8.8/hook",
            "http://1.1.1.1:8080/hook?token=abc",
            "HTTPS://8.8.8.8/hook",
            "https://[2001:4860:4860::8888]/hook",
            "https://100.63.255.255/hook",     // 100.64/10 바로 앞
            "https://100.128.0.1/hook",        // 100.64/10 바로 뒤
            "https://[64:ff9b::808:808]/hook", // NAT64 안의 8.8.8.8
    })
    @DisplayName("공인 주소는 허용한다")
    void 공인_주소(String url) {
        assertThat(policy(false).validate(url).getHost()).isNotBlank();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "http://10.0.0.5/hook",
            "http://172.18.0.1:7080/hook",      // 운영 docker 브리지 게이트웨이 대역
            "http://192.168.0.10/hook",
            "http://100.64.0.1/hook",
            "http://[fc00::1]/hook",
            "http://[fd12:3456::1]/hook",
            "http://100.127.255.255/hook",     // 100.64/10 의 끝 — /16 으로 잘못 판정하면 빠진다
            "http://198.18.0.1/hook",
            "http://[::10.0.0.1]/hook",        // IPv4 호환 IPv6 — netty 는 ::ffff:10.0.0.1 로 연결한다 (반박 리뷰 B1)
            "http://[64:ff9b::a00:1]/hook",    // NAT64 안의 10.0.0.1
    })
    @DisplayName("사설망은 기본으로 막고, allow-private-targets 를 켜면 허용한다 (온프레미스)")
    void 사설망(String url) {
        assertRejected(policy(false), url);
        assertThat(policy(true).validate(url).getHost()).isNotBlank();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "http://127.0.0.1:7432/",
            "http://127.1.2.3/",
            "http://[::1]/",
            "http://0.0.0.0/",
            "http://0.1.2.3/",
            "http://169.254.169.254/latest/meta-data/",   // 클라우드 메타데이터
            "http://[fe80::1]/",
            "http://224.0.0.1/",
            "http://255.255.255.255/",
            "http://[::ffff:127.0.0.1]/",                   // IPv4 매핑 IPv6 로 루프백 우회
            "http://2130706433/",                           // 127.0.0.1 의 10진 표기
            "http://[::]/",                                 // 미지정 — Linux 에서는 로컬호스트로 연결된다
            "http://240.0.0.1/",
            // 반박 리뷰 B1: JDK 는 ::7f00:1 (루프백 아님)로, netty 는 ::ffff:127.0.0.1 로 읽는다.
            "http://[::127.0.0.1]:8888/actuator",
            "http://[0:0:0:0:0:0:127.0.0.1]/",
            "http://[::0:127.0.0.1]/",
            "http://[::169.254.169.254]/latest/meta-data/",
            "http://[::ffff:0:127.0.0.1]/",                 // SIIT
            "http://[64:ff9b::7f00:1]/",                    // NAT64 안의 127.0.0.1
            "http://[2002:7f00:1::1]/",                     // 6to4 안의 127.0.0.1
            "http://[2002:a9fe:a9fe::1]/",                  // 6to4 안의 169.254.169.254
    })
    @DisplayName("루프백·링크 로컬·미지정·멀티캐스트·브로드캐스트는 온프레미스에서도 막는다")
    void 항상_막는_주소(String url) {
        assertRejected(policy(false), url);
        assertRejected(policy(true), url);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {
            "",
            "   ",
            "ftp://8.8.8.8/hook",
            "file:///etc/passwd",
            "gopher://8.8.8.8/",
            "8.8.8.8/hook",           // 스킴 없음
            "https://",
            "https:///path",
            "http://exa mple.com/",   // 문법 오류
            "http://no-such-host.invalid/hook",   // RFC 6761 — 절대 풀리지 않는 이름
    })
    @DisplayName("http(s) 가 아니거나, 형식이 틀렸거나, 호스트를 찾을 수 없으면 막는다")
    void 형식_오류(String url) {
        assertRejected(policy(true), url);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("null 도 같은 오류로 막는다 (NPE 가 아니다)")
    void null_URL() {
        assertRejected(policy(false), null);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("저장할 때: 공인 주소와 내부 주소가 섞인 레코드는 거절한다 — 첫 주소만 보면 뚫린다")
    void 섞인_레코드_저장() {
        WebhookTargetPolicy mixed = new WebhookTargetPolicy(props(false), host -> new InetAddress[] {
                InetAddress.getByName("8.8.8.8"), InetAddress.getByName("10.0.0.1")});
        assertRejected(mixed, "https://mixed.example.com/hook");

        WebhookTargetPolicy publicOnly = new WebhookTargetPolicy(props(false), host -> new InetAddress[] {
                InetAddress.getByName("8.8.8.8"), InetAddress.getByName("1.1.1.1")});
        assertThat(publicOnly.validate("https://ok.example.com/hook").getHost()).isEqualTo("ok.example.com");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("연결할 때: 푼 주소에 막힌 주소가 하나라도 있으면 연결하지 않는다 (DNS 리바인딩)")
    void 섞인_레코드_연결() throws Exception {
        WebhookTargetPolicy policy = policy(false);
        List<InetSocketAddress> mixed = List.of(
                new InetSocketAddress(InetAddress.getByName("8.8.8.8"), 443),
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 443));
        List<InetSocketAddress> ok = List.of(new InetSocketAddress(InetAddress.getByName("8.8.8.8"), 443));

        assertThatThrownBy(() -> policy.selectAllowed(mixed))
                .hasCauseInstanceOf(WebhookTargetPolicy.TargetNotAllowedException.class);
        assertThat(policy.selectAllowed(ok)).isEqualTo(ok);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("보내기 직전 검사는 DNS 를 조회하지 않는다 — 응답 없는 네임서버가 전송 스레드를 붙잡지 않게")
    void 보내기_직전은_조회하지_않는다() {
        WebhookTargetPolicy noLookup = new WebhookTargetPolicy(props(false), host -> {
            throw new AssertionError("DNS 를 조회했다: " + host);
        });
        assertThat(noLookup.checkWithoutLookup("https://receiver.example.com/hook").getHost())
                .isEqualTo("receiver.example.com");
        assertThatThrownBy(() -> noLookup.checkWithoutLookup("http://[::127.0.0.1]/"))
                .isInstanceOf(CustomGateException.class);
    }

    private static void assertRejected(WebhookTargetPolicy policy, String url) {
        assertThatThrownBy(() -> policy.validate(url))
                .isInstanceOf(CustomGateException.class)
                .extracting(e -> ((CustomGateException) e).getErrorType())
                .isEqualTo(ErrorType.WEBHOOK_URL_NOT_ALLOWED);
    }
}

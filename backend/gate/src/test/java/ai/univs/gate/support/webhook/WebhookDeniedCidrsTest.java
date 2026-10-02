package ai.univs.gate.support.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * 웹훅 차단 대역 (UG-348). 공인 주소라 기본 정책을 통과하는 gate 서버 자신의 주소를, 콘솔 「테스트 전송」으로
 * 탐색하지 못하게 막는다. 문서용 주소(RFC 5737·3849)만 쓴다.
 */
@DisplayName("UG-348: 웹훅 차단 대역")
class WebhookDeniedCidrsTest {

    private static WebhookProperties props(boolean allowPrivate, List<String> denied) {
        return new WebhookProperties(
                allowPrivate, Duration.ofSeconds(3), Duration.ofSeconds(5), 3, Duration.ofSeconds(1), 50, 500, 1000, denied);
    }

    private static WebhookTargetPolicy policy(String... denied) {
        return new WebhookTargetPolicy(props(false, List.of(denied)));
    }

    private static InetAddress ip(String literal) throws Exception {
        return InetAddress.getByName(literal);   // 리터럴이라 DNS 를 타지 않는다
    }

    @Test
    @DisplayName("대역 안의 공인 주소는 막고, 바로 바깥은 그대로 둔다")
    void 대역_경계() throws Exception {
        WebhookTargetPolicy p = policy("203.0.113.0/24", "198.51.100.7");

        assertThat(p.isAllowed(ip("203.0.113.0"))).isFalse();
        assertThat(p.isAllowed(ip("203.0.113.255"))).isFalse();
        assertThat(p.isAllowed(ip("198.51.100.7"))).isFalse();
        assertThat(p.isAllowed(ip("203.0.112.255"))).isTrue();
        assertThat(p.isAllowed(ip("203.0.114.0"))).isTrue();
        assertThat(p.isAllowed(ip("198.51.100.8"))).as("접두 길이 없는 값은 주소 하나만").isTrue();
    }

    @Test
    @DisplayName("비어 있으면 아무것도 더 막지 않는다 — 기본값")
    void 기본값() throws Exception {
        assertThat(policy().isAllowed(ip("203.0.113.7"))).isTrue();
        assertThat(new WebhookTargetPolicy(props(false, null)).isAllowed(ip("203.0.113.7"))).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "https://203.0.113.7/hook",
            "https://203.0.113.7:8443/actuator",
            "http://[::ffff:203.0.113.7]/hook",   // IPv4 매핑 IPv6 로 우회
            "http://[64:ff9b::cb00:7107]/hook",   // NAT64 안의 203.0.113.7
            "http://[2002:cb00:7107::1]/hook",    // 6to4 안의 203.0.113.7
    })
    @DisplayName("IP 를 직접 넣어도, IPv6 안에 감싸도 저장할 때 막는다")
    void 저장_리터럴(String url) {
        assertRejected(policy("203.0.113.0/24"), url);
    }

    @Test
    @DisplayName("호스트 이름이 대역 안으로 풀리면 저장할 때 막는다 — 섞인 레코드도")
    void 저장_호스트_이름() throws Exception {
        InetAddress denied = ip("203.0.113.7");
        InetAddress open = ip("8.8.8.8");
        var p = new WebhookTargetPolicy(props(false, List.of("203.0.113.0/24")),
                host -> new InetAddress[] {open, denied});

        assertRejected(p, "https://receiver.example.com/hook");
    }

    @Test
    @DisplayName("보내기 직전(DNS 없이)에도 리터럴은 막는다 — 차단 전에 저장된 URL")
    void 보내기_직전() {
        assertThatThrownBy(() -> policy("203.0.113.0/24").checkWithoutLookup("https://203.0.113.7/hook"))
                .isInstanceOf(CustomGateException.class);
    }

    @Test
    @DisplayName("사설망을 허용한 설치(온프레미스)에서도 차단 대역이 이긴다")
    void 사설망_허용과_무관() throws Exception {
        var p = new WebhookTargetPolicy(props(true, List.of("10.20.0.0/16")));

        assertThat(p.isAllowed(ip("10.20.3.4"))).isFalse();
        assertThat(p.isAllowed(ip("10.21.0.1"))).isTrue();
    }

    @Test
    @DisplayName("IPv6 대역 — 안의 IPv4 를 풀기 전에 먼저 맞춘다")
    void IPv6_대역() throws Exception {
        WebhookTargetPolicy p = policy("2001:db8::/32", "2002::/16");

        assertThat(p.isAllowed(ip("2001:db8:1::5"))).isFalse();
        assertThat(p.isAllowed(ip("2001:db9::5"))).isTrue();
        assertThat(p.isAllowed(ip("2002:0808:0808::1"))).as("6to4 안의 8.8.8.8 이라도 대역이 우선").isFalse();
    }

    @Test
    @DisplayName("거절 문구는 다른 거절과 같다 — 어느 목록에 걸렸는지 알리지 않는다")
    void 문구() {
        CustomGateException denied = catchRejected(policy("203.0.113.0/24"), "https://203.0.113.7/hook");
        CustomGateException loopback = catchRejected(policy(), "http://127.0.0.1/hook");

        assertThat(denied.getErrorType()).isEqualTo(ErrorType.WEBHOOK_URL_NOT_ALLOWED);
        assertThat(denied.getMessageKey()).isEqualTo(loopback.getMessageKey());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "203.0.113.0/33",
            "2001:db8::/129",
            "203.0.113.7/24",      // 접두 뒤에 비트가 남았다 — 203.0.113.0/24 의 오타일 수 있다
            "203.0.113.0/",
            "203.0.113.0/abc",
            "203.0.113.0/-1",
            "gate.univsgate.com",  // 호스트 이름은 받지 않는다
            "203.0.113",
            "300.0.113.0/24",
    })
    @DisplayName("잘못된 값은 기동 실패다 — 조용히 건너뛰면 막았다고 믿는 주소가 열린다")
    void 잘못된_값(String value) {
        assertThatThrownBy(() -> policy(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gate.webhook.denied-cidrs");
    }

    @Test
    @DisplayName("환경변수처럼 쉼표로 이은 값을 목록으로 받는다 — 공백·끝 쉼표는 무시")
    void 바인딩() throws Exception {
        var source = new MapConfigurationPropertySource(
                Map.of("gate.webhook.denied-cidrs", " 203.0.113.0/24 , [2001:db8::]/32,"));

        WebhookProperties bound = new Binder(source).bind("gate.webhook", Bindable.of(WebhookProperties.class)).get();
        WebhookTargetPolicy p = new WebhookTargetPolicy(bound);

        assertThat(p.isAllowed(ip("203.0.113.9"))).isFalse();
        assertThat(p.isAllowed(ip("2001:db8::9"))).isFalse();
        assertThat(p.isAllowed(ip("8.8.8.8"))).isTrue();
    }

    @Test
    @DisplayName("설정이 없으면 빈 목록으로 묶인다 — 다른 기본값도 그대로")
    void 바인딩_기본값() {
        WebhookProperties bound = new Binder(new MapConfigurationPropertySource(Map.of("gate.webhook.max-attempts", "3")))
                .bind("gate.webhook", Bindable.of(WebhookProperties.class)).get();

        assertThat(bound.deniedCidrs()).isEmpty();
        assertThat(bound.allowPrivateTargets()).isFalse();
    }

    private static CustomGateException catchRejected(WebhookTargetPolicy policy, String url) {
        try {
            policy.validate(url);
        } catch (CustomGateException e) {
            return e;
        }
        throw new AssertionError("거절되지 않았다: " + url);
    }

    private static void assertRejected(WebhookTargetPolicy policy, String url) {
        assertThat(catchRejected(policy, url).getErrorType()).isEqualTo(ErrorType.WEBHOOK_URL_NOT_ALLOWED);
    }
}

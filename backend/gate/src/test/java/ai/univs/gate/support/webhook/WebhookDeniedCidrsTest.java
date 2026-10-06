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
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

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
    @DisplayName("클라우드: 거절 문구는 다른 거절과 같다 — 어느 목록에 걸렸는지 알리지 않는다")
    void 문구_클라우드() {
        CustomGateException denied = catchRejected(policy("203.0.113.0/24"), "https://203.0.113.7/hook");
        CustomGateException byName = catchRejected(new WebhookTargetPolicy(props(false, List.of("203.0.113.0/24")),
                host -> new InetAddress[] {InetAddress.getByAddress(new byte[] {(byte) 203, 0, 113, 7})}),
                "https://receiver.example.com/hook");
        CustomGateException loopback = catchRejected(policy(), "http://127.0.0.1/hook");

        assertThat(denied.getErrorType()).isEqualTo(ErrorType.WEBHOOK_URL_NOT_ALLOWED);
        assertThat(loopback.getMessageKey()).as("기본 키(ErrorType 이름)를 쓴다").isEqualTo("WEBHOOK_URL_NOT_ALLOWED");
        assertThat(denied.getMessageKey()).isEqualTo("WEBHOOK_URL_NOT_ALLOWED");
        assertThat(byName.getMessageKey()).isEqualTo("WEBHOOK_URL_NOT_ALLOWED");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "http://10.20.3.4/hook",
            "http://[::ffff:10.20.3.4]/hook",
            "http://[64:ff9b::a14:304]/hook",
    })
    @DisplayName("온프레미스: 차단 대역이면 「사내망 주소로 등록하라」 대신 별도 문구 — 이미 사내망 주소를 넣었다 (반박 리뷰 N1)")
    void 문구_온프레미스(String url) {
        var p = new WebhookTargetPolicy(props(true, List.of("10.20.0.0/16")));

        assertThat(catchRejected(p, url).getMessageKey()).isEqualTo(WebhookTargetPolicy.MESSAGE_KEY_DENIED_RANGE);
        assertThat(catchRejected(p, "http://127.0.0.1/hook").getMessageKey())
                .as("루프백은 기존 안내 그대로").isEqualTo(WebhookTargetPolicy.MESSAGE_KEY_PRIVATE_ALLOWED);
    }

    @Test
    @DisplayName("온프레미스: 호스트 이름이 차단 대역으로 풀려도 같은 별도 문구")
    void 문구_온프레미스_호스트_이름() throws Exception {
        var p = new WebhookTargetPolicy(props(true, List.of("10.20.0.0/16")),
                host -> new InetAddress[] {InetAddress.getByAddress(new byte[] {10, 20, 3, 4})});

        assertThat(catchRejected(p, "https://receiver.corp.example/hook").getMessageKey())
                .isEqualTo(WebhookTargetPolicy.MESSAGE_KEY_DENIED_RANGE);
    }

    @ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', value = {
            "ko|허용되지 않은 주소입니다. 다른 주소를 쓰거나 관리자에게 문의해 주세요.",
            "en|This address isn't allowed. Use a different address or contact your administrator.",
    })
    @DisplayName("별도 문구는 두 번들에 있고, 테스트 전송 문구와 같은 결이다 (기획 10/2 17:52)")
    void 문구_번역(String bundle, String expected) throws Exception {
        var props = new java.util.Properties();
        try (var in = getClass().getResourceAsStream("/messages_" + bundle + ".properties")) {
            props.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        assertThat(props.getProperty(WebhookTargetPolicy.MESSAGE_KEY_DENIED_RANGE)).isEqualTo(expected);
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
            // 반박 리뷰 W1: 판정할 주소는 4바이트 IPv4 라 16바이트로 적힌 대역은 아무것도 막지 않는다
            "::ffff:203.0.113.0/120",
            "::ffff:203.0.113.7",
            "::203.0.113.0/120",          // IPv4 호환 — netty 가 ::ffff: 로 바꾼다
            // 반박 리뷰 N4: 판정에 쓰이지 않거나 파서마다 다르게 읽는 값
            "fe80::%eth0/10",
            "203.0.113.010",
            "010.0.0.0/8",
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

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", " ", ","})
    @DisplayName("빈 환경변수 GATE_WEBHOOK_DENIED_CIDRS 는 빈 목록이다 — 기동 실패가 아니다 (반박 리뷰 N3)")
    void 빈_환경변수(String value) throws Exception {
        WebhookProperties bound = bindFromEnv(Map.of("GATE_WEBHOOK_DENIED_CIDRS", value));

        assertThat(new WebhookTargetPolicy(bound).isAllowed(ip("203.0.113.7"))).isTrue();
    }

    @Test
    @DisplayName("환경변수 이름 GATE_WEBHOOK_DENIED_CIDRS 로 바인딩된다")
    void 환경변수_이름() throws Exception {
        WebhookProperties bound = bindFromEnv(Map.of("GATE_WEBHOOK_DENIED_CIDRS", "203.0.113.0/24,198.51.100.7"));
        WebhookTargetPolicy p = new WebhookTargetPolicy(bound);

        assertThat(p.isAllowed(ip("203.0.113.9"))).isFalse();
        assertThat(p.isAllowed(ip("198.51.100.7"))).isFalse();
        assertThat(p.isAllowed(ip("198.51.100.8"))).isTrue();
    }

    @Test
    @DisplayName("::/0 은 IPv6 만 막는다 — IPv4 는 IPv4 대역으로 적는다 (문서화한 동작)")
    void IPv6_전체() throws Exception {
        WebhookTargetPolicy p = policy("::/0");

        assertThat(p.isAllowed(ip("2001:4860:4860::8888"))).isFalse();
        assertThat(p.isAllowed(ip("8.8.8.8"))).isTrue();
    }

    private static WebhookProperties bindFromEnv(Map<String, Object> envVars) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, envVars));
        return new Binder(ConfigurationPropertySources.get(env))
                .bind("gate.webhook", Bindable.of(WebhookProperties.class)).get();
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

package ai.univs.gate.support.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import java.time.Duration;
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

    private static WebhookTargetPolicy policy(boolean allowPrivate) {
        return new WebhookTargetPolicy(new WebhookProperties(
                allowPrivate, Duration.ofSeconds(3), Duration.ofSeconds(5), 3, Duration.ofSeconds(1), 50, 500, 1000));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "https://8.8.8.8/hook",
            "http://1.1.1.1:8080/hook?token=abc",
            "HTTPS://8.8.8.8/hook",
            "https://[2001:4860:4860::8888]/hook",
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

    private static void assertRejected(WebhookTargetPolicy policy, String url) {
        assertThatThrownBy(() -> policy.validate(url))
                .isInstanceOf(CustomGateException.class)
                .extracting(e -> ((CustomGateException) e).getErrorType())
                .isEqualTo(ErrorType.WEBHOOK_URL_NOT_ALLOWED);
    }
}

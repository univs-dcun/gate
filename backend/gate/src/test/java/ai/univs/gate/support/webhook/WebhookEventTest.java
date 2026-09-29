package ai.univs.gate.support.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.modules.feature.domain.enums.MatchType;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UG-111: 웹훅 이벤트 이름 계약")
class WebhookEventTest {

    @Test
    @DisplayName("UG-111 이전부터 나가던 네 이름은 MatchType 이름과 같다 — 데모 화면(Redis)과 기존 수신 측 계약")
    void 기존_이름_유지() {
        assertThat(WebhookEvent.LIVENESS.name()).isEqualTo(MatchType.LIVENESS.name());
        assertThat(WebhookEvent.VERIFY_ID.name()).isEqualTo(MatchType.VERIFY_ID.name());
        assertThat(WebhookEvent.VERIFY_IMAGE.name()).isEqualTo(MatchType.VERIFY_IMAGE.name());
        assertThat(WebhookEvent.IDENTIFY.name()).isEqualTo(MatchType.IDENTIFY.name());
    }

    @Test
    @DisplayName("이벤트 목록이 고정돼 있다 — 바꾸면 API 문서의 웹훅 절도 함께 고친다")
    void 목록_고정() {
        assertThat(Arrays.stream(WebhookEvent.values()).map(Enum::name))
                .containsExactly("LIVENESS", "VERIFY_ID", "VERIFY_IMAGE", "IDENTIFY",
                        "VERIFY_DESCRIPTOR", "IDENTIFY_DESCRIPTOR", "IDENTIFY_CANDIDATES_DESCRIPTOR");
    }
}

package ai.univs.gate.support.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.facade.demo.application.service.DemoRedisPublisher;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.support.webhook.WebhookEvent;
import ai.univs.gate.support.webhook.WebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UG-111: 결과 알림 경로")
class UseCaseNotifyServiceTest {

    private final DemoRedisPublisher redis = mock(DemoRedisPublisher.class);
    private final WebhookService webhook = mock(WebhookService.class);
    private final UseCaseNotifyService service = new UseCaseNotifyService(redis, webhook, new ObjectMapper());

    @Test
    @DisplayName("데모 호출은 데모 화면 알림과 웹훅 양쪽으로 간다")
    void 데모() {
        Map<String, Object> result = Map.of("k", "v");

        service.notify(CallerType.DEMO, WebhookEvent.LIVENESS, 1L, "tx", result);

        verify(redis).publish(anyString());
        verify(webhook).send(1L, CallerType.DEMO, WebhookEvent.LIVENESS, "tx", result);
    }

    @Test
    @DisplayName("API 호출은 웹훅으로만 간다")
    void API() {
        Map<String, Object> result = Map.of();

        service.notify(CallerType.API, WebhookEvent.IDENTIFY_DESCRIPTOR, 1L, "tx", result);

        verify(redis, never()).publish(anyString());
        verify(webhook).send(1L, CallerType.API, WebhookEvent.IDENTIFY_DESCRIPTOR, "tx", result);
    }

    @Test
    @DisplayName("웹훅 쪽이 예외를 던져도 결과는 그대로 돌아온다 — 알림은 부수 효과다")
    void 웹훅_실패() {
        doThrow(new IllegalStateException("boom")).when(webhook).send(any(), any(), any(), any(), any());
        Map<String, Object> result = Map.of("k", "v");

        assertThat(service.notify(CallerType.API, WebhookEvent.IDENTIFY, 1L, "tx", result)).isSameAs(result);
    }
}

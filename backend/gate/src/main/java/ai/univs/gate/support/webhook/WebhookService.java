package ai.univs.gate.support.webhook;

import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookConfigRepository webhookConfigRepository;
    private final ObjectMapper objectMapper;

    private final WebClient webClient = WebClient.create();

    @Async
    public void send(Long projectId, String webhookType, String event, Object data) {
        webhookConfigRepository.findByProjectId(projectId)
                .ifPresent(config -> {
                    boolean enabled = switch (webhookType.toLowerCase()) {
                        case "demo" -> config.getDemoEnabled();
                        case "sdk"  -> config.getSdkEnabled();
                        case "api"  -> config.getApiEnabled();
                        default     -> false;
                    };

                    if (!enabled) return;

                    try {
                        JsonNode dataNode = objectMapper.valueToTree(data);

                        Map<String, Object> payload = new HashMap<>();
                        payload.put("event", event);
                        payload.put("data", dataNode);

                        webClient.post()
                                .uri(config.getWebhookUrl())
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(payload)
                                .retrieve()
                                .toBodilessEntity()
                                .block();
                        log.info("Webhook sent: projectId={}, type={}, event={}", projectId, webhookType, event);
                    } catch (Exception e) {
                        log.error("Webhook delivery failed: projectId={}, type={}, event={}, error={}",
                                projectId, webhookType, event, e.getMessage());
                    }
                });
    }
}

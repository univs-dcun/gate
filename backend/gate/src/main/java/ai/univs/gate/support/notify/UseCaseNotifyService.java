package ai.univs.gate.support.notify;

import ai.univs.gate.facade.demo.application.dto.DemoRedisPayload;
import ai.univs.gate.facade.demo.application.service.DemoRedisPublisher;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.support.webhook.WebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UseCaseNotifyService {

    private final DemoRedisPublisher demoRedisPublisher;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    public <T> T notify(CallerType callerType,
                        String event,
                        Long projectId,
                        String transactionUuid,
                        T result
    ) {
        switch (callerType) {
            case DEMO -> {
                try {
                    var payload = new DemoRedisPayload<>(event, transactionUuid, result);
                    demoRedisPublisher.publish(objectMapper.writeValueAsString(payload));
                } catch (Exception e) {
                    log.error("failure notify: event={}", event, e);
                }
            }
            case SDK, API -> webhookService.send(projectId, callerType.name(), event, result);
        }
        return result;
    }
}


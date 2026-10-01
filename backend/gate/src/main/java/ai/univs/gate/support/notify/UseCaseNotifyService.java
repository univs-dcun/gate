package ai.univs.gate.support.notify;

import ai.univs.gate.facade.demo.application.dto.DemoRedisPayload;
import ai.univs.gate.facade.demo.application.service.DemoRedisPublisher;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.support.webhook.WebhookEvent;
import ai.univs.gate.support.webhook.WebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 결과를 호출 경로에 맞는 곳으로 알린다.
 *
 * <ul>
 *   <li>데모: 데모 화면 실시간 알림(Redis → notify-service 웹소켓) + 웹훅(데모 토글이 켜져 있으면)
 *   <li>API: 웹훅(API 토글이 켜져 있으면)
 * </ul>
 *
 * <p>UG-111 이전에는 데모 결과가 Redis 로만 갔다. 화면의 데모 토글을 켜도 웹훅이 가지 않았다.
 *
 * <p>어느 쪽이 실패해도 결과 반환을 막지 않는다 — 알림은 부수 효과다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UseCaseNotifyService {

    private final DemoRedisPublisher demoRedisPublisher;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    public <T> T notify(CallerType callerType,
                        WebhookEvent event,
                        Long projectId,
                        String transactionUuid,
                        T result
    ) {
        // switch 로 쓴다 — {@link CallerType#DEMO} 리터럴은 facade.demo 밖에서 금지다 (ApiKeyOwnershipGuardTest).
        switch (callerType) {
            case DEMO -> {
                try {
                    var payload = new DemoRedisPayload<>(event.name(), transactionUuid, result);
                    demoRedisPublisher.publish(objectMapper.writeValueAsString(payload));
                } catch (Exception e) {
                    log.error("failure notify: event={}", event, e);
                }
            }
            case API -> { }
        }
        notifyWebhook(callerType, event, projectId, transactionUuid, result);
        return result;
    }

    /**
     * 웹훅으로만 알린다 — 특징점 등록·삭제처럼 데모 화면이 기다리지 않는 결과용이다 (UG-345).
     *
     * <p>데모 화면 실시간 알림은 매칭·라이브니스 결과 화면이 {@code event} 로 분기해 받는다. 거기에 모르는
     * 이벤트를 흘리면 화면이 엉뚱한 결과로 그릴 수 있어 보내지 않는다. 데모 토글이 켜져 있으면 웹훅은 간다.
     */
    public void notifyWebhook(CallerType callerType,
                              WebhookEvent event,
                              Long projectId,
                              String transactionUuid,
                              Object result
    ) {
        try {
            webhookService.send(projectId, callerType, event, transactionUuid, result);
        } catch (Exception e) {
            log.error("failure webhook enqueue: event={}", event, e);
        }
    }
}

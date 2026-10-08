package ai.univs.gate.modules.webhook.application.usecase;

import ai.univs.gate.modules.webhook.application.result.WebhookConfigResult;
import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.project.ProjectService;
import ai.univs.gate.support.webhook.WebhookSecrets;
import ai.univs.gate.support.webhook.WebhookToggleCache;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전송 대상 토글(API·데모)만 저장한다 (UG-344, 기획 2026-10-01 17:54 — 토글은 누르는 즉시 반영).
 *
 * <p>URL 을 다시 검사하지 않는다. 저장(PUT)으로 토글을 보내면 누를 때마다 URL 의 주소 조회가 다시 돌아, 토글이 느려지거나
 * 일시적인 조회 실패로 토글까지 실패한다. URL 은 저장할 때 검사했고, 보낼 때도 다시 본다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateWebhookTogglesUseCase {

    private final ProjectService projectService;
    private final WebhookConfigRepository webhookConfigRepository;
    private final WebhookToggleCache webhookToggleCache;

    @Transactional
    public WebhookConfigResult execute(Long projectId, Boolean demoEnabled, Boolean apiEnabled) {
        if (demoEnabled == null && apiEnabled == null) {
            throw new CustomGateException(ErrorType.INVALID_INPUT);
        }
        UserContext ctx = UserContext.get();
        // 저장·재발급과 같은 순서로 잠근다(프로젝트 → 설정). 이 트랜잭션도 행 전체를 다시 쓰므로, 잠그지 않으면 그사이
        // 다른 탭의 재발급이나 URL 저장을 낡은 값으로 덮어쓴다.
        projectService.validateOwnershipForUpdate(projectId, ctx.getAccountIdAsLong());
        WebhookConfig config = webhookConfigRepository.findForUpdateByProjectId(projectId)
                .orElseThrow(() -> new CustomGateException(ErrorType.WEBHOOK_CONFIG_NOT_FOUND));
        config.updateToggles(demoEnabled, apiEnabled);
        config.assignSecretIfAbsent(WebhookSecrets.generate());
        // 켠 즉시 전송되게 전송 쪽이 기억한 「꺼짐」을 커밋 뒤에 지운다 (UG-361). 다른 gate 인스턴스는 최대 10초 늦다.
        webhookToggleCache.evictAfterCommit(projectId);
        log.info("Webhook toggles updated: projectId={}, demoEnabled={}, apiEnabled={}", projectId, demoEnabled, apiEnabled);
        return WebhookConfigResult.from(config, LocalDateTime.now(ZoneOffset.UTC));
    }
}

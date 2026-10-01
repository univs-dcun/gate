package ai.univs.gate.modules.webhook.application.usecase;

import ai.univs.gate.modules.webhook.application.result.WebhookConfigResult;
import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.project.ProjectService;
import ai.univs.gate.support.webhook.WebhookSecrets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 웹훅 서명 키 재발급 (UG-344).
 *
 * <p>재발급 뒤 {@link #OVERLAP} 동안은 옛 키로도 함께 서명한다 (Stripe 방식, 사용자 결정 2026-10-01). 수신 측이 새 키를
 * 배포하기 전에 온 웹훅도 검증에 통과해야 키 교체로 수신이 끊기지 않는다. 키가 새어 나가 재발급하는 경우라면 옛 키도
 * 24시간 유효하다는 뜻이므로, 그 기간 동안 수신 측이 옛 키를 먼저 지우면 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RotateWebhookSecretUseCase {

    static final Duration OVERLAP = Duration.ofHours(24);

    private final ProjectService projectService;
    private final WebhookConfigRepository webhookConfigRepository;

    @Transactional
    public WebhookConfigResult execute(Long projectId) {
        UserContext ctx = UserContext.get();
        projectService.validateOwnership(projectId, ctx.getAccountIdAsLong());

        // 잠근다 — 동시에 두 번 누르면 옛 키 자리를 서로 덮어 한쪽 결과의 키가 말없이 버려진다
        WebhookConfig config = webhookConfigRepository.findForUpdateByProjectId(projectId)
                .orElseThrow(() -> new CustomGateException(ErrorType.WEBHOOK_CONFIG_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        config.rotateSecret(WebhookSecrets.generate(), now, OVERLAP);
        log.info("Webhook secret rotated: projectId={}", projectId);
        return WebhookConfigResult.from(config, now);
    }
}

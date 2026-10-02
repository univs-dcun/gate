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
 * 배포하기 전에 온 웹훅도 검증에 통과해야 키 교체로 수신이 끊기지 않는다.
 *
 * <p>키가 새어 나간 경우를 위해 「이전 키 즉시 폐기」를 고를 수 있다 (기획 2026-10-01 17:21). 그때는 옛 키를 남기지
 * 않아, 수신 측이 새 키로 바꿀 때까지 검증이 실패한다 — 화면이 경고한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RotateWebhookSecretUseCase {

    static final Duration OVERLAP = Duration.ofHours(24);

    private final ProjectService projectService;
    private final WebhookConfigRepository webhookConfigRepository;

    @Transactional
    public WebhookConfigResult execute(Long projectId, boolean revokePrevious) {
        UserContext ctx = UserContext.get();
        // 저장과 같은 순서로 잠근다(프로젝트 → 설정) — 저장·재발급이 서로를 기다리기만 하고 교착하지 않게
        projectService.validateOwnershipForUpdate(projectId, ctx.getAccountIdAsLong());

        // 잠근다 — 동시에 두 번 누르면 옛 키 자리를 서로 덮어 한쪽 결과의 키가 말없이 버려진다
        WebhookConfig config = webhookConfigRepository.findForUpdateByProjectId(projectId)
                .orElseThrow(() -> new CustomGateException(ErrorType.WEBHOOK_CONFIG_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        config.rotateSecret(WebhookSecrets.generate(), now, revokePrevious ? Duration.ZERO : OVERLAP);
        log.info("Webhook secret rotated: projectId={}, revokePrevious={}", projectId, revokePrevious);
        return WebhookConfigResult.from(config, now);
    }
}

package ai.univs.gate.modules.webhook.application.usecase;

import ai.univs.gate.support.webhook.WebhookSecrets;
import ai.univs.gate.support.webhook.WebhookTargetPolicy;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.webhook.application.input.UpsertWebhookConfigInput;
import ai.univs.gate.modules.webhook.application.result.WebhookConfigResult;
import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import ai.univs.gate.support.project.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpsertWebhookConfigUseCase {

    private final ProjectService projectService;
    private final WebhookConfigRepository webhookConfigRepository;
    private final WebhookTargetPolicy webhookTargetPolicy;

    @Transactional
    public WebhookConfigResult execute(UpsertWebhookConfigInput input) {
        // 소유 확인은 잠그지 않고 먼저 한다 — 남의 프로젝트로 아래 DNS 조회를 일으키지 못하게.
        projectService.validateOwnership(input.projectId(), input.accountId());
        // UG-111: 내부 주소·http(s) 아닌 주소는 저장하지 않는다. 보낼 때도 다시 검사하지만,
        // 여기서 막아야 화면이 바로 알려 준다 — 보낼 때 막히면 로그에만 남는다.
        // 잠그기 <b>전에</b> 한다 (2차 반박 리뷰 W-b) — 호스트 이름이면 DNS 를 블로킹으로 조회한다. 프로젝트 행을 쥔 채
        // 응답 없는 네임서버를 기다리면 그 프로젝트의 수정·삭제·재발급이 리졸버 타임아웃 동안 모두 멈춘다.
        webhookTargetPolicy.validate(input.webhookUrl());
        // UG-344 반박 리뷰 W1: 그다음 프로젝트 행을 잠근다. 설정이 아직 없으면 아래 FOR UPDATE 는 잠글 행이 없어,
        // 저장을 동시에 두 번 하면 둘 다 INSERT 로 간다. V38 의 유니크 제약이 두 번째를 막지만 그건 500 이다 —
        // 잠가서 두 번째가 첫 번째의 행을 보고 수정으로 가게 한다. 순서는 DeleteProjectUseCase 와 같다(프로젝트 → 하위 행).
        Project project = projectService.validateOwnershipForUpdate(input.projectId(), input.accountId());

        // UG-344: 행을 잠근다. 이 트랜잭션은 키 컬럼까지 통째로 다시 쓰므로, 그사이 전송 쪽이 채운 키나 다른 탭의
        // 재발급을 읽은 값으로 덮어쓰면 수신 측이 가진 키가 말없이 무효가 된다.
        Optional<WebhookConfig> existing = webhookConfigRepository.findForUpdateByProjectId(input.projectId());

        WebhookConfig config;
        if (existing.isEmpty()) {
            config = WebhookConfig.builder()
                    .project(project)
                    .webhookUrl(input.webhookUrl())
                    .demoEnabled(input.demoEnabled())
                    .apiEnabled(input.apiEnabled())
                    .webhookSecret(WebhookSecrets.generate())
                    .build();
            webhookConfigRepository.save(config);
            log.info("Webhook config created: projectId={}", input.projectId());
        } else {
            config = existing.get();
            config.update(
                    input.webhookUrl(),
                    input.demoEnabled(),
                    input.apiEnabled());
            // UG-344 이전에 만든 설정은 키가 없다
            config.assignSecretIfAbsent(WebhookSecrets.generate());
            log.info("Webhook config updated: projectId={}", input.projectId());
        }

        return WebhookConfigResult.from(config, LocalDateTime.now(ZoneOffset.UTC));
    }
}

package ai.univs.gate.modules.webhook.api.controller;

import ai.univs.gate.modules.webhook.api.dto.WebhookConfigRequestDTO;
import ai.univs.gate.modules.webhook.api.dto.WebhookConfigResponseDTO;
import ai.univs.gate.modules.webhook.application.input.UpsertWebhookConfigInput;
import ai.univs.gate.modules.webhook.application.usecase.DeleteWebhookConfigUseCase;
import ai.univs.gate.modules.webhook.application.usecase.GetWebhookConfigUseCase;
import ai.univs.gate.modules.webhook.application.usecase.UpsertWebhookConfigUseCase;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.web.dto.ResponseApi;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/webhook")
public class WebhookConfigController {

    private final GetWebhookConfigUseCase getWebhookConfigUseCase;
    private final UpsertWebhookConfigUseCase upsertWebhookConfigUseCase;
    private final DeleteWebhookConfigUseCase deleteWebhookConfigUseCase;

    @GetMapping
    public ResponseEntity<ResponseApi<WebhookConfigResponseDTO>> getWebhookConfig(
            @PathVariable Long projectId
    ) {
        var result = getWebhookConfigUseCase.execute(projectId);
        var response = result != null ? WebhookConfigResponseDTO.from(result) : null;
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @PutMapping
    public ResponseEntity<ResponseApi<WebhookConfigResponseDTO>> upsertWebhookConfig(
            @PathVariable Long projectId,
            @Valid @RequestBody WebhookConfigRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = new UpsertWebhookConfigInput(
                ctx.getAccountIdAsLong(),
                projectId,
                request.webhookUrl(),
                request.demoEnabled(),
                request.sdkEnabled(),
                request.apiEnabled());

        var result = upsertWebhookConfigUseCase.execute(input);
        return ResponseEntity.ok(ResponseApi.ok(WebhookConfigResponseDTO.from(result)));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteWebhookConfig(
            @PathVariable Long projectId
    ) {
        deleteWebhookConfigUseCase.execute(projectId);
        return ResponseEntity.noContent().build();
    }
}

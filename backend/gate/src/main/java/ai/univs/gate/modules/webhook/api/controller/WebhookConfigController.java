package ai.univs.gate.modules.webhook.api.controller;

import ai.univs.gate.modules.webhook.api.dto.WebhookConfigRequestDTO;
import ai.univs.gate.modules.webhook.api.dto.WebhookConfigResponseDTO;
import ai.univs.gate.modules.webhook.application.input.UpsertWebhookConfigInput;
import ai.univs.gate.modules.webhook.application.usecase.DeleteWebhookConfigUseCase;
import ai.univs.gate.modules.webhook.application.usecase.GetWebhookConfigUseCase;
import ai.univs.gate.modules.webhook.application.usecase.UpsertWebhookConfigUseCase;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.swagger.SwaggerError;
import ai.univs.gate.shared.swagger.SwaggerErrorExample;
import ai.univs.gate.shared.web.dto.ResponseApi;
import ai.univs.gate.shared.web.enums.ErrorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "웹훅 설정")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/webhook")
public class WebhookConfigController {

    private final GetWebhookConfigUseCase getWebhookConfigUseCase;
    private final UpsertWebhookConfigUseCase upsertWebhookConfigUseCase;
    private final DeleteWebhookConfigUseCase deleteWebhookConfigUseCase;

    @Operation(summary = "웹훅 설정 조회", description = "프로젝트의 웹훅 설정을 조회합니다. 설정이 없으면 null을 반환합니다.")
    @SecurityRequirements({@SecurityRequirement(name = "Authentication")})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 403),
    })
    @GetMapping
    public ResponseEntity<ResponseApi<WebhookConfigResponseDTO>> getWebhookConfig(
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
            @PathVariable Long projectId
    ) {
        var result = getWebhookConfigUseCase.execute(projectId);
        var response = result != null ? WebhookConfigResponseDTO.from(result) : null;
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "웹훅 설정 저장", description = "웹훅 설정을 등록하거나 수정합니다. 기존 설정이 없으면 생성, 있으면 수정합니다.")
    @SecurityRequirements({@SecurityRequirement(name = "Authentication")})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 403),
    })
    @PutMapping
    public ResponseEntity<ResponseApi<WebhookConfigResponseDTO>> upsertWebhookConfig(
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
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

    @Operation(summary = "웹훅 설정 삭제", description = "프로젝트의 웹훅 설정을 삭제합니다.")
    @SecurityRequirements({@SecurityRequirement(name = "Authentication")})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 403),
    })
    @DeleteMapping
    public ResponseEntity<Void> deleteWebhookConfig(
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
            @PathVariable Long projectId
    ) {
        deleteWebhookConfigUseCase.execute(projectId);
        return ResponseEntity.noContent().build();
    }
}

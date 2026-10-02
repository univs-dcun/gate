package ai.univs.gate.modules.webhook.api.controller;

import ai.univs.gate.modules.webhook.api.dto.RotateWebhookSecretRequestDTO;
import ai.univs.gate.modules.webhook.api.dto.WebhookConfigRequestDTO;
import ai.univs.gate.modules.webhook.api.dto.WebhookConfigResponseDTO;
import ai.univs.gate.modules.webhook.api.dto.WebhookTestResponseDTO;
import ai.univs.gate.modules.webhook.api.dto.WebhookTogglesRequestDTO;
import ai.univs.gate.modules.webhook.application.input.UpsertWebhookConfigInput;
import ai.univs.gate.modules.webhook.application.usecase.DeleteWebhookConfigUseCase;
import ai.univs.gate.modules.webhook.application.usecase.GetWebhookConfigUseCase;
import ai.univs.gate.modules.webhook.application.usecase.RotateWebhookSecretUseCase;
import ai.univs.gate.modules.webhook.application.usecase.SendWebhookTestUseCase;
import ai.univs.gate.modules.webhook.application.usecase.UpdateWebhookTogglesUseCase;
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
    private final RotateWebhookSecretUseCase rotateWebhookSecretUseCase;
    private final UpdateWebhookTogglesUseCase updateWebhookTogglesUseCase;
    private final SendWebhookTestUseCase sendWebhookTestUseCase;

    @Operation(summary = "웹훅 설정 조회", description = "프로젝트의 웹훅 설정을 조회합니다. 설정이 없으면 null을 반환합니다.")
    @SecurityRequirements({@SecurityRequirement(name = "Authentication")})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 400),
    })
    @GetMapping
    public ResponseEntity<ResponseApi<WebhookConfigResponseDTO>> getWebhookConfig(
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
            @PathVariable Long projectId
    ) {
        var result = getWebhookConfigUseCase.execute(projectId);
        var response = result != null ? WebhookConfigResponseDTO.from(result, UserContext.get().getTimezone()) : null;
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "웹훅 설정 저장", description = "웹훅 설정을 등록하거나 수정합니다. 기존 설정이 없으면 생성, 있으면 수정합니다.")
    @SecurityRequirements({@SecurityRequirement(name = "Authentication")})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.WEBHOOK_URL_NOT_ALLOWED, status = 400),
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 400),
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
                request.apiEnabled());

        var result = upsertWebhookConfigUseCase.execute(input);
        return ResponseEntity.ok(ResponseApi.ok(WebhookConfigResponseDTO.from(result, ctx.getTimezone())));
    }

    @Operation(summary = "웹훅 서명 키 재발급",
            description = "새 서명 키를 발급합니다. 기본은 재발급 후 24시간 동안 이전 키로도 함께 서명해(X-Gate-Signature 에 v1 이 두 개) "
                    + "수신 측이 끊김 없이 키를 바꿀 수 있습니다. 본문에 revokePrevious=true 를 주면 이전 키를 즉시 폐기합니다(키 유출 대응). "
                    + "웹훅 설정이 없으면 PJ-110 입니다.")
    @SecurityRequirements({@SecurityRequirement(name = "Authentication")})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.WEBHOOK_CONFIG_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 400),
    })
    @PostMapping("/secret/rotate")
    public ResponseEntity<ResponseApi<WebhookConfigResponseDTO>> rotateWebhookSecret(
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
            @PathVariable Long projectId,
            @RequestBody(required = false) RotateWebhookSecretRequestDTO request
    ) {
        boolean revokePrevious = request != null && Boolean.TRUE.equals(request.revokePrevious());
        var result = rotateWebhookSecretUseCase.execute(projectId, revokePrevious);
        return ResponseEntity.ok(ResponseApi.ok(WebhookConfigResponseDTO.from(result, UserContext.get().getTimezone())));
    }

    @Operation(summary = "웹훅 전송 대상 토글 저장",
            description = "API·데모 토글만 저장합니다(바꾼 값만 보내기). URL 은 그대로이며 다시 검사하지 않습니다. "
                    + "둘 다 없으면 PJ-101, 웹훅 설정이 없으면 PJ-110 입니다.")
    @SecurityRequirements({@SecurityRequirement(name = "Authentication")})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.WEBHOOK_CONFIG_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 400),
    })
    @PatchMapping
    public ResponseEntity<ResponseApi<WebhookConfigResponseDTO>> updateWebhookToggles(
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
            @PathVariable Long projectId,
            @RequestBody WebhookTogglesRequestDTO request
    ) {
        var result = updateWebhookTogglesUseCase.execute(projectId, request.demoEnabled(), request.apiEnabled());
        return ResponseEntity.ok(ResponseApi.ok(WebhookConfigResponseDTO.from(result, UserContext.get().getTimezone())));
    }

    @Operation(summary = "웹훅 테스트 전송",
            description = "저장된 URL 로 TEST 이벤트 한 건을 서명을 붙여 보내고 결과를 돌려줍니다. 재시도하지 않으며 최대 약 9초 기다립니다. "
                    + "토글과 무관하게 보냅니다. 같은 프로젝트에서 직전 테스트가 끝나고 3초 안에 다시 요청하거나, 같은 계정의 테스트가 "
                    + "아직 진행 중이거나, 전체 동시 요청이 많으면 PJ-112 입니다. "
                    + "수신 서버가 실패해도 이 API 는 200 이고, 결과는 result 로 구분합니다.")
    @SecurityRequirements({@SecurityRequirement(name = "Authentication")})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.WEBHOOK_CONFIG_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.WEBHOOK_TEST_TOO_FREQUENT, status = 400),
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 400),
    })
    @PostMapping("/test")
    public ResponseEntity<ResponseApi<WebhookTestResponseDTO>> sendWebhookTest(
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(ResponseApi.ok(WebhookTestResponseDTO.from(sendWebhookTestUseCase.execute(projectId))));
    }

    @Operation(summary = "웹훅 설정 삭제", description = "프로젝트의 웹훅 설정을 삭제합니다.")
    @SecurityRequirements({@SecurityRequirement(name = "Authentication")})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 400),
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

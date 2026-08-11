package ai.univs.gate.modules.api_key.api.controller;

import ai.univs.gate.modules.api_key.api.dto.ApiKeyResponseDTO;
import ai.univs.gate.modules.api_key.application.usecase.GetApiKeyUseCase;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "API KEY")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/projects/{projectId}/api-key")
public class ApiKeyController {

    private final GetApiKeyUseCase getApiKeyUseCase;

    @Operation(summary = "API Key 조회", description = "프로젝트의 API Key 를 조회합니다 (마스킹 처리)")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 400),
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
    })
    @GetMapping
    public ResponseEntity<ResponseApi<ApiKeyResponseDTO>> getApiKey(
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
            @PathVariable Long projectId
    ) {
        UserContext userContext = UserContext.get();
        var result = getApiKeyUseCase.execute(userContext.getAccountIdAsLong(), projectId);
        var response = ApiKeyResponseDTO.from(result, userContext.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

}

package ai.univs.gate.modules.project.api.controller;

import ai.univs.gate.modules.project.api.dto.*;
import ai.univs.gate.modules.project.application.input.UpdateLivenessSettingsInput;
import ai.univs.gate.modules.project.application.usecase.*;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "프로젝트 설정")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/projects/{projectId}/settings")
public class ProjectSettingsController {

    private final GetProjectSettingsUseCase getProjectSettingsUseCase;
    private final UpdateConsentSettingsUseCase updateConsentSettingsUseCase;
    private final GetConsentLogsUseCase getConsentLogsUseCase;
    private final UpdateLivenessSettingsUseCase updateLivenessSettingsUseCase;

    @Operation(summary = "프로젝트 설정 조회", description = "프로젝트의 모든 설정을 조회합니다")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @GetMapping
    public ResponseEntity<ResponseApi<ProjectSettingsResponseDTO>> getSettings(
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
            @PathVariable Long projectId
    ) {
        var result = getProjectSettingsUseCase.execute(projectId);
        var response = ProjectSettingsResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "개인정보 동의 설정", description = "프로젝트의 개인정보 동의 설정을 변경합니다")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @PutMapping("/consent")
    public ResponseEntity<ResponseApi<ProjectSettingsResponseDTO>> updateConsentSettings(
            HttpServletRequest httpServletRequest,
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
            @PathVariable Long projectId,
            @Valid @RequestBody ConsentSettingsUpdateRequestDTO request
    ) {
        String ipAddress = resolveClientIp(httpServletRequest);
        var result = updateConsentSettingsUseCase.execute(projectId, request.consentEnabled(), ipAddress);
        var response = ProjectSettingsResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "개인정보 동의 변경 이력 조회", description = "프로젝트의 개인정보 동의 변경 이력을 최신순으로 반환합니다")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 400),
    })
    @GetMapping("/consent/logs")
    public ResponseEntity<ResponseApi<ConsentLogListResponseDTO>> getConsentLogs(
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
            @PathVariable Long projectId
    ) {
        var results = getConsentLogsUseCase.execute(projectId);
        var response = ConsentLogListResponseDTO.of(
                results.stream().map(ConsentLogResponseDTO::from).toList());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "라이브니스 사용 여부 설정", description = "프로젝트의 라이브니스 기능 사용 여부를 설정합니다")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @PutMapping("/liveness")
    public ResponseEntity<ResponseApi<ProjectSettingsResponseDTO>> updateLivenessSettings(
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
            @PathVariable Long projectId,
            @Valid @RequestBody LivenessSettingsUpdateRequestDTO request
    ) {
        var operationSettings = request.settings().stream()
                .map(s -> new UpdateLivenessSettingsInput.OperationSetting(s.operation(), s.enabled()))
                .toList();
        var input = new UpdateLivenessSettingsInput(projectId, request.moduleType(), operationSettings);

        var result = updateLivenessSettingsUseCase.execute(input);
        var response = ProjectSettingsResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

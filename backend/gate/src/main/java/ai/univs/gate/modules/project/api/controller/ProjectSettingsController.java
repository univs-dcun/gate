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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "프로젝트 설정")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/projects/{projectId}/settings")
public class ProjectSettingsController {

    private final GetProjectSettingsUseCase getProjectSettingsUseCase;
    private final UpdateConsentSettingsUseCase updateConsentSettingsUseCase;
    private final UpdateLivenessSettingsUseCase updateLivenessSettingsUseCase;
    private final UpdateDemoSettingsUseCase updateDemoSettingsUseCase;
    private final UpdateSdkSettingsUseCase updateSdkSettingsUseCase;

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
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
            @PathVariable Long projectId,
            @Valid @RequestBody ConsentSettingsUpdateRequestDTO request
    ) {
        var result = updateConsentSettingsUseCase.execute(projectId, request.consentEnabled());
        var response = ProjectSettingsResponseDTO.from(result);
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
        var input = new UpdateLivenessSettingsInput(
                projectId,
                request.livenessRecordingEnabled(),
                request.livenessIdentifyingEnabled(),
                request.livenessVerifyingByIdEnabled(),
                request.livenessVerifyingByImageEnabled());

        var result = updateLivenessSettingsUseCase.execute(input);
        var response = ProjectSettingsResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "데모 사용 여부 설정", description = "데모 사용 여부를 설정합니다")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @PutMapping("/demo")
    public ResponseEntity<ResponseApi<ProjectSettingsResponseDTO>> updateDemoSettings(
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
            @PathVariable Long projectId,
            @Valid @RequestBody DemoSettingsUpdateRequestDTO request
    ) {
        var result = updateDemoSettingsUseCase.execute(projectId, request.demoEnabled());
        var response = ProjectSettingsResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "SDK 사용 여부 설정", description = "SDK 사용 여부를 설정합니다")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @PutMapping("/sdk")
    public ResponseEntity<ResponseApi<ProjectSettingsResponseDTO>> updateDemoSettings(
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
            @PathVariable Long projectId,
            @Valid @RequestBody SdkSettingsUpdateRequestDTO request
    ) {
        var result = updateSdkSettingsUseCase.execute(projectId, request.sdkEnabled());
        var response = ProjectSettingsResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

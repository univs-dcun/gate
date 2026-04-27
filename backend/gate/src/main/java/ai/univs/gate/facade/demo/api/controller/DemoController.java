package ai.univs.gate.facade.demo.api.controller;

import ai.univs.gate.facade.demo.api.dto.*;
import ai.univs.gate.facade.demo.application.dto.DemoRedisPayload;
import ai.univs.gate.facade.demo.application.service.DemoRedisPublisher;
import ai.univs.gate.facade.demo.application.usecase.CreateUserByApiKeyUseCase;
import ai.univs.gate.facade.demo.application.usecase.GetDemoProjectConfigUseCase;
import ai.univs.gate.modules.match.api.dto.IdentifyResponseDTO;
import ai.univs.gate.modules.match.api.dto.LivenessResponseDTO;
import ai.univs.gate.modules.match.api.dto.VerifyByFaceIdResponseDTO;
import ai.univs.gate.modules.match.api.dto.VerifyByImageResponseDTO;
import ai.univs.gate.modules.match.application.usecase.IdentifyUseCase;
import ai.univs.gate.modules.match.application.usecase.LivenessUseCase;
import ai.univs.gate.modules.match.application.usecase.VerifyByFaceIdUseCase;
import ai.univs.gate.modules.match.application.usecase.VerifyByImageUseCase;
import ai.univs.gate.modules.project.api.dto.ProjectSettingsResponseDTO;
import ai.univs.gate.modules.user.api.dto.UserResponseDTO;
import ai.univs.gate.shared.swagger.SwaggerError;
import ai.univs.gate.shared.swagger.SwaggerErrorExample;
import ai.univs.gate.shared.web.dto.ResponseApi;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.message.MessageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "게이트 데모")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/demo")
public class DemoController {

    private final CreateUserByApiKeyUseCase createUserByApiKeyUseCase;
    private final VerifyByFaceIdUseCase verifyByFaceIdUseCase;
    private final VerifyByImageUseCase verifyByImageUseCase;
    private final IdentifyUseCase identifyUseCase;
    private final LivenessUseCase livenessUseCase;
    private final GetDemoProjectConfigUseCase getDemoProjectConfigUseCase;
    private final MessageService messageService;
    private final DemoRedisPublisher demoRedisPublisher;
    private final ObjectMapper objectMapper;

    @Operation(
            summary = "API Key 기반 프로젝트 설정 조회",
            description = "API Key 기반으로 프로젝트 이름 및 라이브니스 설정을 조회합니다."
    )
    @SecurityRequirements({
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @GetMapping("/config")
    public ResponseEntity<ResponseApi<ProjectSettingsResponseDTO>> getProjectConfigByApiKey(
            HttpServletRequest httpServletRequest,
            @RequestBody DemoProjectConfigRequestDTO request
    ) {
        String timezone = httpServletRequest.getHeader("Accept-TimeZone");
        var result = getDemoProjectConfigUseCase.execute(request.apiKey(), timezone);
        var response = ProjectSettingsResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "API Key 기반 사용자 등록")
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/user", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<UserResponseDTO>> createUserByApiKey(
            HttpServletRequest httpServletRequest,
            @ParameterObject @ModelAttribute @Valid CreateUserByApiKeyRequestDTO request
    ) throws JsonProcessingException {
        var input = request.toCreateUserByApiKeyInput();
        var result = createUserByApiKeyUseCase.execute(input);
        var response = UserResponseDTO.from(result, httpServletRequest.getHeader("Accept-TimeZone"));
        var responseApi = ResponseApi.ok(response);

        var payload = new DemoRedisPayload<>("REGISTER", result.transactionUuid(), responseApi);
        demoRedisPublisher.publish(objectMapper.writeValueAsString(payload));

        return ResponseEntity.ok(responseApi);
    }

    @Operation(summary = "API Key, faceId 기반 사용자 확인")
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<VerifyByFaceIdResponseDTO>> verifyByApiKey(
            HttpServletRequest httpServletRequest,
            @ParameterObject @ModelAttribute @Valid VerifyByApiKeyRequestDTO request
    ) {
        String timezone = httpServletRequest.getHeader("Accept-TimeZone");
        var input = request.toVerifyByApiKeyInput();
        var result = verifyByFaceIdUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = VerifyByFaceIdResponseDTO.from(result, failureReason, timezone);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "API Key, image 기반 사용자 확인")
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/verify/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<VerifyByImageResponseDTO>> verifyByImageAndApiKey(
            HttpServletRequest httpServletRequest,
            @ParameterObject @ModelAttribute @Valid VerifyByImageAndApiKeyRequestDTO request
    ) {
        String timezone = httpServletRequest.getHeader("Accept-TimeZone");
        var input = request.toVerifyByImageInput();
        var result = verifyByImageUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = VerifyByImageResponseDTO.from(result, failureReason, timezone);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "API Key 기반 사용자 매칭")
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<IdentifyResponseDTO>> identifyByApiKey(
            HttpServletRequest httpServletRequest,
            @ParameterObject @ModelAttribute @Valid DemoIdentifyRequestDTO request
    ) {
        String timezone = httpServletRequest.getHeader("Accept-TimeZone");
        var input = request.toIdentifyInput();
        var result = identifyUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = IdentifyResponseDTO.from(result, failureReason, timezone);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "API Key 기반 라이브니스 체크")
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/liveness", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<LivenessResponseDTO>> LivenessByApiKey(
            @ParameterObject @ModelAttribute @Valid LivenessByApiKeyRequestDTO request
    ) {
        var input = request.toLivenessInput();
        var result = livenessUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.prdioctionDesc());
        var response = LivenessResponseDTO.from(result, failureReason);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

package ai.univs.gate.facade.demo.api.controller;

import ai.univs.gate.facade.demo.api.dto.*;
import ai.univs.gate.facade.demo.application.dto.DemoRedisPayload;
import ai.univs.gate.facade.demo.application.service.DemoRedisPublisher;
import ai.univs.gate.facade.demo.application.usecase.CreateFaceFeatureByApiKeyUseCase;
import ai.univs.gate.facade.demo.application.usecase.CreatePalmFeatureByApiKeyUseCase;
import ai.univs.gate.facade.demo.application.usecase.GetDemoProjectConfigUseCase;
import ai.univs.gate.facade.demo.application.usecase.GetFaceFeaturesByApiKeyUseCase;
import ai.univs.gate.facade.demo.application.usecase.GetPalmFeaturesByApiKeyUseCase;
import ai.univs.gate.modules.face_feature.api.dto.IdentifyResponseDTO;
import ai.univs.gate.modules.face_feature.api.dto.LivenessResponseDTO;
import ai.univs.gate.modules.face_feature.api.dto.VerifyByFaceIdResponseDTO;
import ai.univs.gate.modules.face_feature.api.dto.VerifyByImageResponseDTO;
import ai.univs.gate.modules.face_feature.application.usecase.FaceIdentifyUseCase;
import ai.univs.gate.modules.face_feature.application.usecase.FaceLivenessUseCase;
import ai.univs.gate.modules.face_feature.application.usecase.FaceVerifyByFeatureIdUseCase;
import ai.univs.gate.modules.face_feature.application.usecase.FaceVerifyByFeatureImageUseCase;
import ai.univs.gate.modules.palm_feature.api.dto.PalmFeatureResponseDTO;
import ai.univs.gate.modules.palm_feature.api.dto.PalmFeaturesResponseDTO;
import ai.univs.gate.modules.palm_feature.api.dto.PalmIdentifyResponseDTO;
import ai.univs.gate.modules.palm_feature.api.dto.PalmLivenessResponseDTO;
import ai.univs.gate.modules.palm_feature.application.usecase.PalmIdentifyUseCase;
import ai.univs.gate.modules.palm_feature.application.usecase.PalmLivenessUseCase;
import ai.univs.gate.modules.project.api.dto.ProjectSettingsResponseDTO;
import ai.univs.gate.modules.face_feature.api.dto.FaceFeatureResponseDTO;
import ai.univs.gate.modules.face_feature.api.dto.FaceFeaturesResponseDTO;
import ai.univs.gate.shared.web.dto.CustomPage;
import ai.univs.gate.shared.swagger.SwaggerError;
import ai.univs.gate.shared.swagger.SwaggerErrorExample;
import ai.univs.gate.shared.web.dto.ResponseApi;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.message.MessageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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

@Tag(name = "e-KYC 데모")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/demo")
public class DemoController {

    private final CreateFaceFeatureByApiKeyUseCase createFaceFeatureByApiKeyUseCase;
    private final GetFaceFeaturesByApiKeyUseCase getFaceFeaturesByApiKeyUseCase;
    private final FaceVerifyByFeatureIdUseCase faceVerifyByFeatureIdUseCase;
    private final FaceVerifyByFeatureImageUseCase faceVerifyByFeatureImageUseCase;
    private final FaceIdentifyUseCase faceIdentifyUseCase;
    private final FaceLivenessUseCase faceLivenessUseCase;
    private final CreatePalmFeatureByApiKeyUseCase createPalmFeatureByApiKeyUseCase;
    private final GetPalmFeaturesByApiKeyUseCase getPalmFeaturesByApiKeyUseCase;
    private final PalmIdentifyUseCase palmIdentifyUseCase;
    private final PalmLivenessUseCase palmLivenessUseCase;
    private final GetDemoProjectConfigUseCase getDemoProjectConfigUseCase;

    private final MessageService messageService;
    private final DemoRedisPublisher demoRedisPublisher;
    private final ObjectMapper objectMapper;

    @Operation(
            summary = "프로젝트 설정 조회",
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

    @Operation(summary = "특징점 얼굴 등록")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = CreateFaceFeatureByApiKeyRequestDTO.class)))
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/feature/face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<FaceFeatureResponseDTO>> createUserByApiKey(
            HttpServletRequest httpServletRequest,
            @ModelAttribute @Valid CreateFaceFeatureByApiKeyRequestDTO request
    ) throws JsonProcessingException {
        var input = request.toCreateUserByApiKeyInput();
        var result = createFaceFeatureByApiKeyUseCase.execute(input);
        var response = FaceFeatureResponseDTO.from(result, httpServletRequest.getHeader("Accept-TimeZone"));
        var responseApi = ResponseApi.ok(response);

        var payload = new DemoRedisPayload<>("REGISTER", result.transactionUuid(), responseApi);
        demoRedisPublisher.publish(objectMapper.writeValueAsString(payload));

        return ResponseEntity.ok(responseApi);
    }

    @Operation(summary = "featureId 기반 특징점 얼굴 확인")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = VerifyByApiKeyRequestDTO.class)))
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/feature/face/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<VerifyByFaceIdResponseDTO>> verifyByApiKey(
            HttpServletRequest httpServletRequest,
            @ModelAttribute @Valid VerifyByApiKeyRequestDTO request
    ) {
        String timezone = httpServletRequest.getHeader("Accept-TimeZone");
        var input = request.toVerifyByApiKeyInput();
        var result = faceVerifyByFeatureIdUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = VerifyByFaceIdResponseDTO.from(result, failureReason, timezone);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "image 기반 특징점 얼굴 확인")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = VerifyByImageAndApiKeyRequestDTO.class)))
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/feature/face/verify/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<VerifyByImageResponseDTO>> verifyByImageAndApiKey(
            HttpServletRequest httpServletRequest,
            @ModelAttribute @Valid VerifyByImageAndApiKeyRequestDTO request
    ) {
        String timezone = httpServletRequest.getHeader("Accept-TimeZone");
        var input = request.toVerifyByImageInput();
        var result = faceVerifyByFeatureImageUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = VerifyByImageResponseDTO.from(result, failureReason, timezone);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "특징점 얼굴 1:N 매칭")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = DemoIdentifyRequestDTO.class)))
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/feature/face/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<IdentifyResponseDTO>> identifyByApiKey(
            HttpServletRequest httpServletRequest,
            @ModelAttribute @Valid DemoIdentifyRequestDTO request
    ) {
        String timezone = httpServletRequest.getHeader("Accept-TimeZone");
        var input = request.toIdentifyInput();
        var result = faceIdentifyUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = IdentifyResponseDTO.from(result, failureReason, timezone);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "특징점 얼굴 목록 조회")
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
    })
    @GetMapping("/feature/faces")
    public ResponseEntity<ResponseApi<FaceFeaturesResponseDTO>> getUsersByApiKey(
            HttpServletRequest httpServletRequest,
            @ParameterObject @ModelAttribute @Valid GetUsersByApiKeyRequestDTO request
    ) {
        String timezone = httpServletRequest.getHeader("Accept-TimeZone");
        var input = request.toInput(timezone);
        var result = getFaceFeaturesByApiKeyUseCase.execute(input);

        var faceFeatureResponses = result.faceFeatures().stream()
                .map(fm -> FaceFeatureResponseDTO.from(fm, timezone))
                .toList();
        var response = new FaceFeaturesResponseDTO(faceFeatureResponses, CustomPage.from(result.page()));
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "특징점 얼굴 라이브니스 체크")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = LivenessByApiKeyRequestDTO.class)))
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/feature/face/liveness", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<LivenessResponseDTO>> LivenessByApiKey(
            @ModelAttribute @Valid LivenessByApiKeyRequestDTO request
    ) {
        var input = request.toLivenessInput();
        var result = faceLivenessUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.prdioctionDesc());
        var response = LivenessResponseDTO.from(result, failureReason);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    // ── Palm Demo ──────────────────────────────────────────────────────────────

    @Operation(summary = "특징점 팜 등록")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = CreatePalmFeatureByApiKeyRequestDTO.class)))
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/feature/palm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<PalmFeatureResponseDTO>> createPalmByApiKey(
            HttpServletRequest httpServletRequest,
            @ModelAttribute @Valid CreatePalmFeatureByApiKeyRequestDTO request
    ) throws com.fasterxml.jackson.core.JsonProcessingException {
        var input = request.toInput();
        var result = createPalmFeatureByApiKeyUseCase.execute(input);
        var response = PalmFeatureResponseDTO.from(result, httpServletRequest.getHeader("Accept-TimeZone"));
        var responseApi = ResponseApi.ok(response);

        var payload = new DemoRedisPayload<>("REGISTER", result.transactionUuid(), responseApi);
        demoRedisPublisher.publish(objectMapper.writeValueAsString(payload));

        return ResponseEntity.ok(responseApi);
    }

    @Hidden
    @Operation(summary = "특징점 팜 목록 조회")
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
    })
    @GetMapping("/feature/palms")
    public ResponseEntity<ResponseApi<PalmFeaturesResponseDTO>> getPalmsByApiKey(
            HttpServletRequest httpServletRequest,
            @ParameterObject @ModelAttribute @Valid GetUsersByApiKeyRequestDTO request
    ) {
        String timezone = httpServletRequest.getHeader("Accept-TimeZone");
        var input = request.toInput(timezone);
        var result = getPalmFeaturesByApiKeyUseCase.execute(input);

        var palmFeatureResponses = result.palmFeatures().stream()
                .map(pm -> PalmFeatureResponseDTO.from(pm, timezone))
                .toList();
        var response = new PalmFeaturesResponseDTO(palmFeatureResponses, CustomPage.from(result.page()));
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "특징점 팜 1:N 매칭")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = DemoPalmIdentifyRequestDTO.class)))
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/feature/palm/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<PalmIdentifyResponseDTO>> palmIdentifyByApiKey(
            HttpServletRequest httpServletRequest,
            @ModelAttribute @Valid DemoPalmIdentifyRequestDTO request
    ) {
        String timezone = httpServletRequest.getHeader("Accept-TimeZone");
        var input = request.toInput();
        var result = palmIdentifyUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = PalmIdentifyResponseDTO.from(result, failureReason, timezone);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "특징점 팜 라이브니스")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = DemoPalmLivenessRequestDTO.class)))
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/feature/palm/liveness", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<PalmLivenessResponseDTO>> palmLivenessByApiKey(
            @ModelAttribute @Valid DemoPalmLivenessRequestDTO request
    ) {
        var input = request.toInput();
        var result = palmLivenessUseCase.execute(input);
        String failureReason = result.success() ? "" : messageService.getFailureMessageOrEmpty(result.message());
        var response = PalmLivenessResponseDTO.from(result, failureReason);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

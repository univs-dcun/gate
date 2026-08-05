package ai.univs.gate.modules.feature.api.controller;

import ai.univs.gate.modules.feature.api.dto.face.*;
import ai.univs.gate.modules.feature.application.input.face.DeleteFaceFeatureInput;
import ai.univs.gate.modules.feature.application.input.face.GetFaceFeatureByFeatureIdInput;
import ai.univs.gate.modules.feature.application.input.face.GetFaceFeatureInput;
import ai.univs.gate.modules.feature.application.usecase.face.*;
import ai.univs.gate.modules.feature.api.dto.CreateFeatureRequestDTO;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.swagger.SwaggerError;
import ai.univs.gate.shared.swagger.SwaggerErrorExample;
import ai.univs.gate.shared.web.dto.CustomPage;
import ai.univs.gate.shared.web.dto.ResponseApi;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.message.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "특징점 얼굴 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/feature/face")
public class FaceController {

    private final CreateFaceFeatureUseCase createFaceFeatureUseCase;
    private final UpdateFaceFeatureUseCase updateFaceFeatureUseCase;
    private final DeleteFaceFeatureUseCase deleteFaceFeatureUseCase;
    private final GetFaceFeatureUseCase getFaceFeatureUseCase;
    private final GetFaceFeatureByFaceIdUseCase getFaceFeatureByFaceIdUseCase;
    private final GetFaceFeaturesUseCase getFaceFeaturesUseCase;
    private final ExtractUseCase extractUseCase;
    private final FaceVerifyByFeatureIdUseCase faceVerifyByFeatureIdUseCase;
    private final FaceVerifyByFeatureImageUseCase faceVerifyByFeatureImageUseCase;
    private final VerifyByDescriptorUseCase verifyByDescriptorUseCase;
    private final IdentifyFaceUseCase identifyFaceUseCase;
    private final LivenessFaceUseCase livenessFaceUseCase;
    private final CreateFaceFeatureByDescriptorUseCase createFaceFeatureByDescriptorUseCase;
    private final IdentifyByDescriptorUseCase identifyByDescriptorUseCase;
    private final MessageService messageService;

    @Operation(summary = "특징점 얼굴 등록")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = CreateFaceFeatureRequestDTO.class)))
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<FaceFeatureResponseDTO>> create(
            @ModelAttribute @Valid CreateFeatureRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toInput(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = createFaceFeatureUseCase.execute(input);
        var response = FaceFeatureResponseDTO.from(result, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(
            summary = "특징점 얼굴 등록 (특징점 기반)",
            description = "이미지 대신 특징점 추출 API 가 반환한 descriptor 를 그대로 전달합니다. "
                    + "descriptor 가 존재한다는 것은 추출·라이브니스 단계가 이미 끝났다는 뜻이므로 "
                    + "프로젝트의 라이브니스 설정과 무관하게 라이브니스를 수행하지 않습니다."
    )
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
    })
    @PostMapping(value = "/descriptor", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseApi<FaceFeatureByDescriptorResponseDTO>> createByDescriptor(
            @org.springframework.web.bind.annotation.RequestBody @Valid CreateFaceFeatureByDescriptorRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toInput(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = createFaceFeatureByDescriptorUseCase.execute(input);
        var response = FaceFeatureByDescriptorResponseDTO.from(result, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "특징점 얼굴 수정")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = UpdateFaceFeatureRequestDTO.class)))
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_USER, status = 400),
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
    })
    @PutMapping(value = "/{faceFeatureId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<FaceFeatureResponseDTO>> update(
            @Parameter(description = "특징점 얼굴 ID") @PathVariable Long faceFeatureId,
            @ModelAttribute @Valid UpdateFaceFeatureRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toInput(ctx.getAccountIdAsLong(), ctx.getApiKey(), faceFeatureId);
        var result = updateFaceFeatureUseCase.execute(input);
        var response = FaceFeatureResponseDTO.from(result, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "특징점 얼굴 삭제")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_USER, status = 400),
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
    })
    @DeleteMapping("/{faceFeatureId}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "특징점 얼굴 ID") @PathVariable Long faceFeatureId
    ) {
        UserContext ctx = UserContext.get();
        var input = new DeleteFaceFeatureInput(ctx.getAccountIdAsLong(), ctx.getApiKey(), faceFeatureId);
        deleteFaceFeatureUseCase.execute(input);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "특징점 얼굴 단건 조회")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @GetMapping("/{faceFeatureId}")
    public ResponseEntity<ResponseApi<FaceFeatureResponseDTO>> get(
            @Parameter(description = "특징점 얼굴 ID") @PathVariable Long faceFeatureId
    ) {
        UserContext ctx = UserContext.get();
        var input = new GetFaceFeatureInput(ctx.getAccountIdAsLong(), ctx.getApiKey(), faceFeatureId);
        var result = getFaceFeatureUseCase.execute(input);
        var response = FaceFeatureResponseDTO.from(result, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "Face ID 기반 특징점 얼굴 조회")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @GetMapping("/faceId/{faceId}")
    public ResponseEntity<ResponseApi<FaceFeatureResponseDTO>> getByFeatureId(
            @Parameter(description = "Face ID") @PathVariable String faceId
    ) {
        UserContext ctx = UserContext.get();
        var input = new GetFaceFeatureByFeatureIdInput(ctx.getAccountIdAsLong(), ctx.getApiKey(), faceId);
        var result = getFaceFeatureByFaceIdUseCase.execute(input);
        var response = FaceFeatureResponseDTO.from(result, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "특징점 얼굴 목록 조회")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @GetMapping
    public ResponseEntity<ResponseApi<FaceFeaturesResponseDTO>> list(
            @ParameterObject @ModelAttribute @Valid FaceFeatureSelectCondition condition
    ) {
        UserContext ctx = UserContext.get();
        var query = condition.toQuery(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = getFaceFeaturesUseCase.execute(query);

        List<FaceFeatureResponseDTO> faceFeatureResponses = result.faceFeatures().stream()
                .map(fm -> FaceFeatureResponseDTO.from(fm, ctx.getTimezone()))
                .toList();

        var page = CustomPage.from(result.page());
        var response = new FaceFeaturesResponseDTO(faceFeatureResponses, page);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "특징점 추출")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = ExtractRequestDTO.class)))
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
    })
    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<ExtractResponseDTO>> extract(
            @ModelAttribute @Valid ExtractRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toExtractInput(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = extractUseCase.execute(input);
        var response = ExtractResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "얼굴 확인 (featureSeq 기반)")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = VerifyByFaceIdRequestDTO.class)))
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @PostMapping(value = "/verify/id", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<VerifyByFaceIdResponseDTO>> verifyById(
            @ModelAttribute @Valid VerifyByFaceIdRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toVerifyByFaceIdInput(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = faceVerifyByFeatureIdUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = VerifyByFaceIdResponseDTO.from(result, failureReason, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "얼굴 확인 (image 기반)")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = VerifyByImageRequestDTO.class)))
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @PostMapping(value = "/verify/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<VerifyByImageResponseDTO>> verifyByImage(
            @ModelAttribute @Valid VerifyByImageRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toVerifyByImageInput(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = faceVerifyByFeatureImageUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = VerifyByImageResponseDTO.from(result, failureReason, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "얼굴 확인 (특징점 기반)")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @PostMapping(value = "/verify/descriptor")
    public ResponseEntity<ResponseApi<VerifyByDescriptorResponseDTO>> verifyByDescriptor(
            @org.springframework.web.bind.annotation.RequestBody @Valid VerifyByDescriptorRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toVerifyByDescriptorInput(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = verifyByDescriptorUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = VerifyByDescriptorResponseDTO.from(result, failureReason, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "얼굴 1:N 매칭")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = IdentifyRequestDTO.class)))
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @PostMapping(value = "/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<IdentifyResponseDTO>> identify(
            @ModelAttribute @Valid IdentifyRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toIdentifyInput(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = identifyFaceUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = IdentifyResponseDTO.from(result, failureReason, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(
            summary = "얼굴 1:N 매칭 (특징점 기반)",
            description = "이미지 대신 특징점 추출 API 가 반환한 descriptor 를 그대로 전달합니다. "
                    + "라이브니스를 수행하지 않는 이유는 특징점 기반 등록 API 와 같습니다."
    )
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @PostMapping(value = "/identify/descriptor", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseApi<IdentifyByDescriptorResponseDTO>> identifyByDescriptor(
            @org.springframework.web.bind.annotation.RequestBody @Valid IdentifyByDescriptorRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toIdentifyByDescriptorInput(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = identifyByDescriptorUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = IdentifyByDescriptorResponseDTO.from(result, failureReason, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "얼굴 라이브니스")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = LivenessRequestDTO.class)))
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @PostMapping(value = "/liveness", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<LivenessResponseDTO>> liveness(
            @ModelAttribute @Valid LivenessRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toLivenessInput(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = livenessFaceUseCase.execute(input);
        // UG-274: prdioctionDesc 는 성공 시 "REAL" 이다. 성공/실패를 가리지 않고 메시지 변환에
        // 넘기면 messages_{ko,en}.properties 에 REAL 키가 없어 setUseCodeAsDefaultMessage(true)
        // 때문에 코드 문자열 "REAL" 이 그대로 failureReason 으로 나간다. 문서는 성공 시 null 을
        // 공표한다. Palm 라이브니스는 이미 같은 방식으로 막고 있다 (PalmController).
        String failureReason = result.success()
                ? ""
                : messageService.getFailureMessageOrEmpty(result.prdioctionDesc());
        var response = LivenessResponseDTO.from(result, failureReason);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

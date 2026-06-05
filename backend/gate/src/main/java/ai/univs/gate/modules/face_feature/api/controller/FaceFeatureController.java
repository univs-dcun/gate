package ai.univs.gate.modules.face_feature.api.controller;

import ai.univs.gate.modules.face_feature.api.dto.*;
import ai.univs.gate.modules.face_feature.application.input.DeleteFaceFeatureInput;
import ai.univs.gate.modules.face_feature.application.input.GetFaceFeatureByFeatureIdInput;
import ai.univs.gate.modules.face_feature.application.input.GetFaceFeatureInput;
import ai.univs.gate.modules.face_feature.application.usecase.*;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.swagger.SwaggerError;
import ai.univs.gate.shared.swagger.SwaggerErrorExample;
import ai.univs.gate.shared.web.dto.CustomPage;
import ai.univs.gate.shared.web.dto.ResponseApi;
import ai.univs.gate.shared.web.enums.ErrorType;
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
public class FaceFeatureController {

    private final CreateFaceFeatureUseCase createFaceFeatureUseCase;
    private final UpdateFaceFeatureUseCase updateFaceFeatureUseCase;
    private final DeleteFaceFeatureUseCase deleteFaceFeatureUseCase;
    private final GetFaceFeatureUseCase getFaceFeatureUseCase;
    private final GetFaceFeatureByFaceIdUseCase getFaceFeatureByFaceIdUseCase;
    private final GetFaceFeaturesUseCase getFaceFeaturesUseCase;

    @Operation(summary = "특징점 얼굴 등록")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = CreateFaceFeatureRequestDTO.class)))
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<FaceFeatureResponseDTO>> create(
            @ModelAttribute @Valid CreateFaceFeatureRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toInput(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = createFaceFeatureUseCase.execute(input);
        var response = FaceFeatureResponseDTO.from(result, ctx.getTimezone());
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
}

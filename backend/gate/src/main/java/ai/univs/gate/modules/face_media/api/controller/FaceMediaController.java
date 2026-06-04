package ai.univs.gate.modules.face_media.api.controller;

import ai.univs.gate.modules.face_media.api.dto.*;
import ai.univs.gate.modules.face_media.application.input.DeleteFaceMediaInput;
import ai.univs.gate.modules.face_media.application.input.GetFaceMediaByFaceIdInput;
import ai.univs.gate.modules.face_media.application.input.GetFaceMediaInput;
import ai.univs.gate.modules.face_media.application.usecase.*;
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

@Tag(name = "페이스 미디어")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/face-media")
public class FaceMediaController {

    private final CreateFaceMediaUseCase createFaceMediaUseCase;
    private final UpdateFaceMediaUseCase updateFaceMediaUseCase;
    private final DeleteFaceMediaUseCase deleteFaceMediaUseCase;
    private final GetFaceMediaUseCase getFaceMediaUseCase;
    private final GetFaceMediaByFaceIdUseCase getFaceMediaByFaceIdUseCase;
    private final GetFaceMediasUseCase getFaceMediasUseCase;

    @Operation(summary = "페이스 미디어 등록")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = CreateFaceMediaRequestDTO.class)))
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<FaceMediaResponseDTO>> create(
            @ModelAttribute @Valid CreateFaceMediaRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toInput(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = createFaceMediaUseCase.execute(input);
        var response = FaceMediaResponseDTO.from(result, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "페이스 미디어 수정")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = UpdateFaceMediaRequestDTO.class)))
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_USER, status = 400),
    })
    @PutMapping(value = "/{faceMediaId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<FaceMediaResponseDTO>> update(
            @Parameter(description = "페이스 미디어 ID") @PathVariable Long faceMediaId,
            @ModelAttribute @Valid UpdateFaceMediaRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toInput(ctx.getAccountIdAsLong(), ctx.getApiKey(), faceMediaId);
        var result = updateFaceMediaUseCase.execute(input);
        var response = FaceMediaResponseDTO.from(result, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "페이스 미디어 삭제")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_USER, status = 400),
    })
    @DeleteMapping("/{faceMediaId}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "페이스 미디어 ID") @PathVariable Long faceMediaId
    ) {
        UserContext ctx = UserContext.get();
        var input = new DeleteFaceMediaInput(ctx.getAccountIdAsLong(), ctx.getApiKey(), faceMediaId);
        deleteFaceMediaUseCase.execute(input);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "페이스 미디어 단건 조회")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @GetMapping("/{faceMediaId}")
    public ResponseEntity<ResponseApi<FaceMediaResponseDTO>> get(
            @Parameter(description = "페이스 미디어 ID") @PathVariable Long faceMediaId
    ) {
        UserContext ctx = UserContext.get();
        var input = new GetFaceMediaInput(ctx.getAccountIdAsLong(), ctx.getApiKey(), faceMediaId);
        var result = getFaceMediaUseCase.execute(input);
        var response = FaceMediaResponseDTO.from(result, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "faceId 기반 페이스 미디어 조회")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @GetMapping("/faceId/{faceId}")
    public ResponseEntity<ResponseApi<FaceMediaResponseDTO>> getByFaceId(
            @Parameter(description = "Face ID") @PathVariable String faceId
    ) {
        UserContext ctx = UserContext.get();
        var input = new GetFaceMediaByFaceIdInput(ctx.getAccountIdAsLong(), ctx.getApiKey(), faceId);
        var result = getFaceMediaByFaceIdUseCase.execute(input);
        var response = FaceMediaResponseDTO.from(result, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "페이스 미디어 목록 조회")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @GetMapping
    public ResponseEntity<ResponseApi<FaceMediasResponseDTO>> list(
            @ParameterObject @ModelAttribute @Valid FaceMediaSelectCondition condition
    ) {
        UserContext ctx = UserContext.get();
        var query = condition.toQuery(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = getFaceMediasUseCase.execute(query);

        List<FaceMediaResponseDTO> faceMediaResponses = result.faceMedias().stream()
                .map(fm -> FaceMediaResponseDTO.from(fm, ctx.getTimezone()))
                .toList();

        var page = CustomPage.from(result.page());
        var response = new FaceMediasResponseDTO(faceMediaResponses, page);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

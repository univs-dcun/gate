package ai.univs.gate.modules.palm_media.api.controller;

import ai.univs.gate.modules.palm_media.api.dto.*;
import ai.univs.gate.modules.palm_media.application.input.DeletePalmMediaInput;
import ai.univs.gate.modules.palm_media.application.input.GetPalmMediaInput;
import ai.univs.gate.modules.palm_media.application.usecase.*;
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

@Tag(name = "팜 미디어")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/palm-media")
public class PalmMediaController {

    private final CreatePalmMediaUseCase createPalmMediaUseCase;
    private final UpdatePalmMediaUseCase updatePalmMediaUseCase;
    private final DeletePalmMediaUseCase deletePalmMediaUseCase;
    private final GetPalmMediaUseCase getPalmMediaUseCase;
    private final GetPalmMediasUseCase getPalmMediasUseCase;

    @Operation(summary = "팜 등록")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = CreatePalmMediaRequestDTO.class)))
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<PalmMediaResponseDTO>> create(
            @ModelAttribute @Valid CreatePalmMediaRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toInput(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = createPalmMediaUseCase.execute(input);
        var response = PalmMediaResponseDTO.from(result, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "팜 수정")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = UpdatePalmMediaRequestDTO.class)))
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_USER, status = 400),
    })
    @PutMapping(value = "/{palmMediaId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<PalmMediaResponseDTO>> update(
            @Parameter(description = "팜 미디어 ID") @PathVariable Long palmMediaId,
            @ModelAttribute @Valid UpdatePalmMediaRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toInput(ctx.getAccountIdAsLong(), ctx.getApiKey(), palmMediaId);
        var result = updatePalmMediaUseCase.execute(input);
        var response = PalmMediaResponseDTO.from(result, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "팜 삭제")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_USER, status = 400),
    })
    @DeleteMapping("/{palmMediaId}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "팜 미디어 ID") @PathVariable Long palmMediaId
    ) {
        UserContext ctx = UserContext.get();
        var input = new DeletePalmMediaInput(ctx.getAccountIdAsLong(), ctx.getApiKey(), palmMediaId);
        deletePalmMediaUseCase.execute(input);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "팜 단건 조회")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_USER, status = 400),
    })
    @GetMapping("/{palmMediaId}")
    public ResponseEntity<ResponseApi<PalmMediaResponseDTO>> get(
            @Parameter(description = "팜 미디어 ID") @PathVariable Long palmMediaId
    ) {
        UserContext ctx = UserContext.get();
        var input = new GetPalmMediaInput(ctx.getAccountIdAsLong(), ctx.getApiKey(), palmMediaId);
        var result = getPalmMediaUseCase.execute(input);
        var response = PalmMediaResponseDTO.from(result, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "팜 목록 조회")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @GetMapping
    public ResponseEntity<ResponseApi<PalmMediasResponseDTO>> list(
            @ParameterObject @ModelAttribute @Valid PalmMediaSelectCondition condition
    ) {
        UserContext ctx = UserContext.get();
        var query = condition.toQuery(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = getPalmMediasUseCase.execute(query);

        List<PalmMediaResponseDTO> palmMediaResponses = result.palmMedias().stream()
                .map(pm -> PalmMediaResponseDTO.from(pm, ctx.getTimezone()))
                .toList();

        var page = CustomPage.from(result.page());
        var response = new PalmMediasResponseDTO(palmMediaResponses, page);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

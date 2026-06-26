package ai.univs.gate.modules.feature.api.controller;

import ai.univs.gate.modules.palm_feature.api.dto.*;
import ai.univs.gate.modules.palm_feature.application.input.DeletePalmFeatureInput;
import ai.univs.gate.modules.palm_feature.application.input.GetPalmFeatureInput;
import ai.univs.gate.modules.palm_feature.application.usecase.*;
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

@Tag(name = "특징점 팜 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/feature/palm")
public class PalmController {

    private final CreatePalmFeatureUseCase createPalmFeatureUseCase;
    private final UpdatePalmFeatureUseCase updatePalmFeatureUseCase;
    private final DeletePalmFeatureUseCase deletePalmFeatureUseCase;
    private final GetPalmFeatureUseCase getPalmFeatureUseCase;
    private final GetPalmFeaturesUseCase getPalmFeaturesUseCase;
    private final IdentifyPalmUseCase identifyPalmUseCase;
    private final LivenessPalmUseCase livenessPalmUseCase;
    private final MessageService messageService;

    @Operation(summary = "팜 등록")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = CreatePalmFeatureRequestDTO.class)))
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<PalmFeatureResponseDTO>> create(
            @ModelAttribute @Valid CreatePalmFeatureRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toInput(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = createPalmFeatureUseCase.execute(input);
        var response = PalmFeatureResponseDTO.from(result, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "팜 수정")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = UpdatePalmFeatureRequestDTO.class)))
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_USER, status = 400),
    })
    @PutMapping(value = "/{palmFeatureId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<PalmFeatureResponseDTO>> update(
            @Parameter(description = "팜 식별 번호") @PathVariable Long palmFeatureId,
            @ModelAttribute @Valid UpdatePalmFeatureRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toInput(ctx.getAccountIdAsLong(), ctx.getApiKey(), palmFeatureId);
        var result = updatePalmFeatureUseCase.execute(input);
        var response = PalmFeatureResponseDTO.from(result, ctx.getTimezone());
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
    @DeleteMapping("/{palmFeatureId}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "팜 식별 번호") @PathVariable Long palmFeatureId
    ) {
        UserContext ctx = UserContext.get();
        var input = new DeletePalmFeatureInput(ctx.getAccountIdAsLong(), ctx.getApiKey(), palmFeatureId);
        deletePalmFeatureUseCase.execute(input);
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
    @GetMapping("/{palmFeatureId}")
    public ResponseEntity<ResponseApi<PalmFeatureResponseDTO>> get(
            @Parameter(description = "팜 식별 번호") @PathVariable Long palmFeatureId
    ) {
        UserContext ctx = UserContext.get();
        var input = new GetPalmFeatureInput(ctx.getAccountIdAsLong(), ctx.getApiKey(), palmFeatureId);
        var result = getPalmFeatureUseCase.execute(input);
        var response = PalmFeatureResponseDTO.from(result, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "팜 목록 조회")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @GetMapping
    public ResponseEntity<ResponseApi<PalmFeaturesResponseDTO>> list(
            @ParameterObject @ModelAttribute @Valid PalmFeatureSelectCondition condition
    ) {
        UserContext ctx = UserContext.get();
        var query = condition.toQuery(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = getPalmFeaturesUseCase.execute(query);

        List<PalmFeatureResponseDTO> palmFeatureResponses = result.palmFeatures().stream()
                .map(pm -> PalmFeatureResponseDTO.from(pm, ctx.getTimezone()))
                .toList();

        var page = CustomPage.from(result.page());
        var response = new PalmFeaturesResponseDTO(palmFeatureResponses, page);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "팜 매칭")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = PalmIdentifyRequestDTO.class)))
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_MATCH, status = 400),
    })
    @PostMapping(value = "/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<PalmIdentifyResponseDTO>> identify(
            @ModelAttribute @Valid PalmIdentifyRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toInput(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = identifyPalmUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = PalmIdentifyResponseDTO.from(result, failureReason, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "팜 라이브니스")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = PalmLivenessRequestDTO.class)))
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/liveness", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<PalmLivenessResponseDTO>> liveness(
            @ModelAttribute @Valid PalmLivenessRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toInput(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = livenessPalmUseCase.execute(input);
        String failureReason = result.success() ? "" : messageService.getFailureMessageOrEmpty(result.message());
        var response = PalmLivenessResponseDTO.from(result, failureReason);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

package ai.univs.gate.modules.palm_feature.api.controller;

import ai.univs.gate.modules.palm_feature.application.usecase.IdentifyPalmUseCase;
import ai.univs.gate.modules.palm_feature.application.usecase.LivenessPalmUseCase;
import ai.univs.gate.modules.palm_feature.api.dto.PalmIdentifyRequestDTO;
import ai.univs.gate.modules.palm_feature.api.dto.PalmIdentifyResponseDTO;
import ai.univs.gate.modules.palm_feature.api.dto.PalmLivenessRequestDTO;
import ai.univs.gate.modules.palm_feature.api.dto.PalmLivenessResponseDTO;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.swagger.SwaggerError;
import ai.univs.gate.shared.swagger.SwaggerErrorExample;
import ai.univs.gate.shared.web.dto.ResponseApi;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.message.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "특징점 팜 매칭")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/feature/palm/match")
public class PalmMatchController {

    private final IdentifyPalmUseCase identifyPalmUseCase;
    private final LivenessPalmUseCase livenessPalmUseCase;
    private final MessageService messageService;

    @Operation(summary = "팜 1:N 매칭")
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

package ai.univs.gate.modules.match.api.controller;

import ai.univs.gate.modules.match.api.dto.*;
import ai.univs.gate.modules.match.application.usecase.*;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.swagger.SwaggerError;
import ai.univs.gate.shared.swagger.SwaggerErrorExample;
import ai.univs.gate.shared.web.dto.CustomPage;
import ai.univs.gate.shared.web.dto.ResponseApi;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.message.MessageService;
import ai.univs.gate.support.webhook.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@Tag(name = "e-kyc")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/match")
public class matchController {

    private final IdentifyUseCase identifyUseCase;
    private final VerifyByFaceIdUseCase verifyByFaceIdUseCase;
    private final VerifyByImageUseCase verifyByImageUseCase;
    private final GetMatchHistoriesUseCase getMatchHistoriesUseCase;
    private final GetMatchHistoryByTransactionUuidUseCase getMatchHistoryByTransactionUuidUseCase;
    private final LivenessUseCase livenessUseCase;
    private final MessageService messageService;
    private final ApiKeyService apiKeyService;
    private final WebhookService webhookService;

    @Operation(summary = "매칭")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<IdentifyResponseDTO>> identify(
            @ParameterObject @ModelAttribute @Valid IdentifyRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toIdentifyInput(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = identifyUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = IdentifyResponseDTO.from(result, failureReason, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "확인 (faceId 기반)")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/verify/id", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<VerifyByFaceIdResponseDTO>> verifyById(
            @ParameterObject @ModelAttribute @Valid VerifyByFaceIdRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toVerifyByFaceIdInput(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var result = verifyByFaceIdUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = VerifyByFaceIdResponseDTO.from(result, failureReason, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "확인 (image 기반)")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/verify/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<VerifyByImageResponseDTO>> verifyByImage(
            @ParameterObject @ModelAttribute @Valid VerifyByImageRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toVerifyByImageInput(ctx.getAccountIdAsLong(), ctx.getTimezone());
        var result = verifyByImageUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = VerifyByImageResponseDTO.from(result, failureReason, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "라이브니스")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/liveness", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<LivenessResponseDTO>> liveness(
            @ParameterObject @ModelAttribute @Valid LivenessRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toLivenessInput(ctx.getAccountIdAsLong(), ctx.getTimezone());
        var result = livenessUseCase.execute(input);
        String failureReason = messageService.getFailureMessageOrEmpty(result.prdioctionDesc());
        var response = LivenessResponseDTO.from(result, failureReason);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "이력 조회")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @GetMapping
    public ResponseEntity<ResponseApi<MatchingHistoriesResponseDTO>> getView(
            @ParameterObject @ModelAttribute @Valid MatchingHistorySelectCondition condition
    ) {
        UserContext ctx = UserContext.get();
        var input = condition.toMatchingHistoryQuery(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var pagedMatchingHistories = getMatchHistoriesUseCase.execute(input);

        List<MatchingHistoryResponseDTO> contents = pagedMatchingHistories.results().stream()
                .map(history ->
                        MatchingHistoryResponseDTO.from(
                                history,
                                messageService.getFailureMessageOrEmpty(history.failureType()),
                                ctx.getTimezone()
                        )
                )
                .toList();

        var page = CustomPage.from(pagedMatchingHistories.page());
        var response = new MatchingHistoriesResponseDTO(contents, page);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "트렌젝션 UUID 기반 이력 조회")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_FOUND_MATCHING_HISTORY, status = 400),
    })
    @GetMapping("/{transactionUuid}")
    public ResponseEntity<ResponseApi<MatchingHistoryResponseDTO>> getIdViewByTransactionUuid(
            @Parameter(description = SwaggerDescriptions.TRANSACTION_UUID)
            @PathVariable String transactionUuid
    ) {
        UserContext ctx = UserContext.get();
        var result = getMatchHistoryByTransactionUuidUseCase.execute(ctx.getApiKey(), transactionUuid);
        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = MatchingHistoryResponseDTO.from(result, failureReason, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

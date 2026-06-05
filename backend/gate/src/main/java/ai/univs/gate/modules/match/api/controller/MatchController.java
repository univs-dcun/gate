package ai.univs.gate.modules.match.api.controller;

import ai.univs.gate.modules.face_feature.application.usecase.*;
import ai.univs.gate.modules.match.api.dto.*;
import ai.univs.gate.modules.match.application.usecase.*;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.swagger.SwaggerError;
import ai.univs.gate.shared.swagger.SwaggerErrorExample;
import ai.univs.gate.shared.web.dto.CustomPage;
import ai.univs.gate.shared.web.dto.ResponseApi;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.message.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "e-KYC 이력")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/match")
public class MatchController {

    private final GetMatchHistoriesUseCase getMatchHistoriesUseCase;
    private final GetMatchHistoryByTransactionUuidUseCase getMatchHistoryByTransactionUuidUseCase;
    private final MessageService messageService;

    @Operation(summary = "매칭 이력 조회")
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

    @Operation(summary = "트랜잭션 UUID 기반 이력 조회")
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

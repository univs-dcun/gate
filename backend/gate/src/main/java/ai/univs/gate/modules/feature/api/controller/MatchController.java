package ai.univs.gate.modules.feature.api.controller;

import ai.univs.gate.modules.feature.domain.enums.ActivityType;
import ai.univs.gate.modules.feature.application.result.match.MatchHistoryResult;
import ai.univs.gate.modules.feature.api.dto.match.*;
import ai.univs.gate.modules.feature.application.usecase.face.*;
import ai.univs.gate.modules.feature.application.usecase.match.*;
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
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
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
                                failureReason(history),
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
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @GetMapping("/{transactionUuid}")
    public ResponseEntity<ResponseApi<MatchingHistoryResponseDTO>> getIdViewByTransactionUuid(
            @Parameter(description = SwaggerDescriptions.TRANSACTION_UUID)
            @PathVariable String transactionUuid
    ) {
        UserContext ctx = UserContext.get();
        var result = getMatchHistoryByTransactionUuidUseCase.execute(
                ctx.getAccountIdAsLong(), ctx.getApiKey(), transactionUuid);
        var response = MatchingHistoryResponseDTO.from(result, failureReason(result), ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    /**
     * 이력 행의 실패 사유. 라이브니스 행은 라이브니스 API 응답과 같은 규칙으로 만든다 (UG-346 반박 리뷰) — 엔진이 준
     * 모르는 문자열이면 두 곳 모두 「라이브니스 검증에 실패하였습니다」. 일반 대체 문구를 쓰면 같은 거래가 응답과 이력에서
     * 다른 문장으로 보인다.
     */
    private String failureReason(MatchHistoryResult history) {
        return history.matchType() == ActivityType.LIVENESS
                ? messageService.getLivenessFailureMessage(history.failureType())
                : messageService.getFailureMessageOrEmpty(history.failureType());
    }
}

package ai.univs.gate.facade.dashboard.api.controller;

import ai.univs.gate.facade.dashboard.api.dto.*;
import ai.univs.gate.facade.dashboard.application.result.DashboardDailyStatsResult;
import ai.univs.gate.facade.dashboard.application.result.DashboardRatiosResult;
import ai.univs.gate.facade.dashboard.application.result.DashboardSummaryResult;
import ai.univs.gate.facade.dashboard.application.result.DashboardTrendResult;
import ai.univs.gate.facade.dashboard.application.usecase.GetDashboardDailyStatsUseCase;
import ai.univs.gate.facade.dashboard.application.usecase.GetDashboardDemoQrUseCase;
import ai.univs.gate.facade.dashboard.application.usecase.GetDashboardRatiosUseCase;
import ai.univs.gate.facade.dashboard.application.usecase.GetDashboardSummaryUseCase;
import ai.univs.gate.facade.dashboard.application.usecase.GetDashboardTrendUseCase;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.web.dto.ResponseApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "대시보드")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final GetDashboardSummaryUseCase    getDashboardSummaryUseCase;
    private final GetDashboardTrendUseCase      getDashboardTrendUseCase;
    private final GetDashboardDailyStatsUseCase getDashboardDailyStatsUseCase;
    private final GetDashboardRatiosUseCase     getDashboardRatiosUseCase;
    private final GetDashboardDemoQrUseCase     getDashboardDemoQrUseCase;

    @Operation(
            summary = "대시보드 요약 조회",
            description = "선택한 기간의 등록·1:1 확인·1:N 매칭·라이브니스 건수를 반환합니다. period 미입력 시 MONTH 기본값으로 동작합니다."
    )
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @GetMapping("/summary")
    public ResponseEntity<ResponseApi<DashboardSummaryResponse>> getSummary(
            @ParameterObject @ModelAttribute DashboardPeriodRequest request
    ) {
        UserContext ctx = UserContext.get();
        DashboardSummaryResult result = getDashboardSummaryUseCase.execute(ctx.getApiKey(), request.effectivePeriod());
        var response = DashboardSummaryResponse.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(
            summary = "사용량 추이 조회",
            description = "주(7일)·월(30일)·년(12개월) 단위로 4개 유형의 사용 추이 시계열 데이터를 반환합니다. " +
                          "period 미입력 시 WEEK 기본값으로 동작합니다."
    )
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @GetMapping("/trend")
    public ResponseEntity<ResponseApi<DashboardTrendResponse>> getTrend(
            @ParameterObject @ModelAttribute DashboardTrendRequest request
    ) {
        UserContext ctx = UserContext.get();
        DashboardTrendResult result = getDashboardTrendUseCase.execute(ctx.getApiKey(), request.effectivePeriod());
        var response = DashboardTrendResponse.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(
            summary = "비율 통계 조회",
            description = "선택한 기간의 등록/삭제, 1:1 확인 성공/실패, 1:N 매칭 성공/실패, 라이브니스 리얼/페이크 비율을 반환합니다. period 미입력 시 MONTH 기본값으로 동작합니다."
    )
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @GetMapping("/ratios")
    public ResponseEntity<ResponseApi<DashboardRatiosResponse>> getRatios(
            @ParameterObject @ModelAttribute DashboardPeriodRequest request
    ) {
        UserContext ctx = UserContext.get();
        DashboardRatiosResult result = getDashboardRatiosUseCase.execute(ctx.getApiKey(), request.effectivePeriod());
        var response = DashboardRatiosResponse.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(
            summary = "데모 실행 QR 조회",
            description = "대시보드 데모 실행용 QR 코드 이미지(PNG)를 반환합니다."
    )
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @GetMapping(value = "/demo-qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getDemoQr() {
        byte[] qrImage = getDashboardDemoQrUseCase.execute();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(qrImage);
    }

    @Operation(
            summary = "일일 데이터 통계 조회",
            description = "날짜(UTC) 기준 하루 단위 집계 데이터를 최신순 페이징으로 반환합니다."
    )
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @GetMapping("/daily")
    public ResponseEntity<ResponseApi<DashboardDailyStatsResponse>> getDailyStats(
            @ParameterObject @ModelAttribute @Valid DashboardDailyStatsRequest request
    ) {
        UserContext ctx = UserContext.get();
        DashboardDailyStatsResult result = getDashboardDailyStatsUseCase.execute(
                ctx.getApiKey(),
                request.effectivePage(),
                request.effectivePageSize());
        var response = DashboardDailyStatsResponse.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

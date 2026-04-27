package ai.univs.gate.facade.sdk.api.controller;

import ai.univs.gate.facade.sdk.api.dto.*;
import ai.univs.gate.facade.sdk.application.service.SdkQrCodeService;
import ai.univs.gate.facade.sdk.application.usecase.*;
import ai.univs.gate.modules.match.api.dto.IdentifyResponseDTO;
import ai.univs.gate.modules.match.api.dto.LivenessResponseDTO;
import ai.univs.gate.modules.match.api.dto.VerifyByFaceIdResponseDTO;
import ai.univs.gate.modules.project.api.dto.ProjectSettingsResponseDTO;
import ai.univs.gate.modules.user.api.dto.UserResponseDTO;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.jwt.JwtTokenProvider;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.swagger.SwaggerError;
import ai.univs.gate.shared.swagger.SwaggerErrorExample;
import ai.univs.gate.shared.utils.TransactionUtil;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "SDK")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sdk")
public class SdkController {

    private final SdkIdentifyUseCase sdkIdentifyUseCase;
    private final SdkVerifyUseCase sdkVerifyUseCase;
    private final SdkLivenessUseCase sdkLivenessUseCase;

    private final GetSdkProjectConfigUseCase getSdkProjectConfigUseCase;
    private final GetSdkProjectConfigByCodeUseCase getSdkProjectConfigByCodeUseCase;
    private final GetCreateUserQrCodeUseCase getCreateUserQrCodeUseCase;
    private final CreateUserByTokenUseCase createUserByTokenUseCase;
    private final GetVerifyQrCodeUseCase getVerifyQrCodeUseCase;
    private final GetIdentifyQrCodeUseCase getIdentifyQrCodeUseCase;
    private final GetLivenessQrCodeUseCase getLivenessQrCodeUserCase;
    private final JwtTokenProvider jwtTokenProvider;
    private final SdkQrCodeService sdkQrCodeService;
    private final MessageService messageService;
    private final ApiKeyService apiKeyService;
    private final WebhookService webhookService;

    @Operation(
            summary = "API Key 기반 프로젝트 설정 조회",
            description = "API Key 기반으로 프로젝트 이름 및 라이브니스 설정을 조회합니다."
    )
    @SecurityRequirements({
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @GetMapping("/config")
    public ResponseEntity<ResponseApi<ProjectSettingsResponseDTO>> getProjectConfigByApiKey(
            HttpServletRequest httpServletRequest
    ) {
        String timezone = httpServletRequest.getHeader("Accept-TimeZone");
        String apiKey = httpServletRequest.getHeader("X-Api-Key");
        if (!StringUtils.hasText(apiKey)) {
            throw new CustomGateException(ErrorType.API_KEY_NOT_FOUND);
        }

        var result = getSdkProjectConfigUseCase.execute(apiKey, timezone);
        var response = ProjectSettingsResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(
            summary = "코드 기반 프로젝트 설정 조회",
            description = "코드 기반으로 프로젝트 이름 및 라이브니스 설정을 조회합니다."
    )
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @GetMapping("/config/{code}")
    public ResponseEntity<ResponseApi<ProjectSettingsResponseDTO>> getProjectConfigByToken(
            HttpServletRequest httpServletRequest,
            @Parameter(description = SwaggerDescriptions.PROJECT_CONFIG_CODE)
            @PathVariable String code
    ) {
        String timezone = httpServletRequest.getHeader("Accept-TimeZone");
        var result = getSdkProjectConfigByCodeUseCase.execute(code, timezone);
        var response = ProjectSettingsResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "사용자 등록 QR / Link 생성")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @GetMapping(value = "/user/qr")
    public ResponseEntity<ResponseApi<QrCodeResponseDTO>> getCreateUserQrCode() {
        UserContext ctx = UserContext.get();
        var result = getCreateUserQrCodeUseCase.execute(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var response = new QrCodeResponseDTO(result.base64QrCode(), result.link());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "코드 기반 사용자 등록")
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_QR_CODE, status = 400),
            @SwaggerError(errorType = ErrorType.EXPIRED_QR_CODE, status = 400),
    })
    @PostMapping(value = "/user/token", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<UserResponseDTO>> createUserByToken(
            HttpServletRequest httpServletRequest,
            @ParameterObject @ModelAttribute @Valid CreateUserByTokenRequestDTO request
    ) {
        String timezone = httpServletRequest.getHeader("Accept-TimeZone");
        String token = sdkQrCodeService.getToken(request.code());
        String apiKey = jwtTokenProvider.getApiKeyFromToken(token);
        Long projectId = apiKeyService.findByApiKey(apiKey).getProject().getId();

        var input = request.toCreateUserByTokenInput();
        var result = createUserByTokenUseCase.execute(input);
        var response = UserResponseDTO.from(result, timezone);
        var responseApi = ResponseApi.ok(response);

        webhookService.send(projectId, "sdk", "user.register", responseApi);
        sdkQrCodeService.consumeCode(request.code());
        return ResponseEntity.ok(responseApi);
    }

    @Operation(summary = "사용자 확인 QR / Link 생성")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @GetMapping(value = "/verify/qr/{faceId}")
    public ResponseEntity<ResponseApi<QrCodeResponseDTO>> getVerifyQrCode(
            @Parameter(description = SwaggerDescriptions.FACE_ID)
            @PathVariable String faceId
    ) {
        UserContext ctx = UserContext.get();
        var result = getVerifyQrCodeUseCase.execute(ctx.getAccountIdAsLong(), ctx.getApiKey(), faceId);
        var response = new QrCodeResponseDTO(result.base64QrCode(), result.link());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "코드 기반 사용자 확인")
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_QR_CODE, status = 400),
            @SwaggerError(errorType = ErrorType.EXPIRED_QR_CODE, status = 400),
    })
    @PostMapping(value = "/verify/token", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<VerifyByFaceIdResponseDTO>> verifyByToken(
            HttpServletRequest httpServletRequest,
            @ParameterObject @ModelAttribute @Valid VerifyByTokenRequestDTO request
    ) {
        String timezone = httpServletRequest.getHeader("Accept-TimeZone");

        var result = sdkVerifyUseCase.execute(
                request.code(),
                request.matchingFaceImage(),
                TransactionUtil.useOrCreate(request.transactionUuid()));

        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = VerifyByFaceIdResponseDTO.from(result, failureReason, timezone);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "사용자 매칭 QR / Link 생성")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @GetMapping(value = "/identify/qr")
    public ResponseEntity<ResponseApi<QrCodeResponseDTO>> getIdentifyQrCode() {
        UserContext ctx = UserContext.get();
        var result = getIdentifyQrCodeUseCase.execute(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var response = new QrCodeResponseDTO(result.base64QrCode(), result.link());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "코드 기반 사용자 매칭")
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_QR_CODE, status = 400),
            @SwaggerError(errorType = ErrorType.EXPIRED_QR_CODE, status = 400),
    })
    @PostMapping(value = "/identify/token", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<IdentifyResponseDTO>> identifyByToken(
            HttpServletRequest httpServletRequest,
            @ParameterObject @ModelAttribute @Valid IdentifyByTokenRequestDTO request
    ) {
        String timezone = httpServletRequest.getHeader("Accept-TimeZone");

        var result = sdkIdentifyUseCase.execute(
                request.code(),
                request.matchingFaceImage(),
                TransactionUtil.useOrCreate(request.transactionUuid()));

        String failureReason = messageService.getFailureMessageOrEmpty(result.failureType());
        var response = IdentifyResponseDTO.from(result, failureReason, timezone);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "라이브니스 QR / Link 생성")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @GetMapping(value = "/liveness/qr")
    public ResponseEntity<ResponseApi<QrCodeResponseDTO>> getLivenessQrCode() {
        UserContext ctx = UserContext.get();
        var result = getLivenessQrCodeUserCase.execute(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var response = new QrCodeResponseDTO(result.base64QrCode(), result.link());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "코드 기반 라이브니스 체크")
    @SecurityRequirements({})
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_QR_CODE, status = 400),
            @SwaggerError(errorType = ErrorType.EXPIRED_QR_CODE, status = 400),
    })
    @PostMapping(value = "/liveness/token", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<LivenessResponseDTO>> LivenessByToken(
            @ParameterObject @ModelAttribute @Valid LivenessByTokenRequestDTO request
    ) {
        var result = sdkLivenessUseCase.execute(
                request.code(),
                request.matchingFaceImage(),
                TransactionUtil.useOrCreate(request.transactionUuid()));

        String failureReason = messageService.getFailureMessageOrEmpty(result.prdioctionDesc());
        var response = LivenessResponseDTO.from(result, failureReason);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

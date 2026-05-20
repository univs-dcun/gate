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
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.web.dto.ResponseApi;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.message.MessageService;
import ai.univs.gate.support.webhook.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/config/{code}")
    public ResponseEntity<ResponseApi<ProjectSettingsResponseDTO>> getProjectConfigByToken(
            HttpServletRequest httpServletRequest,
            @PathVariable String code
    ) {
        String timezone = httpServletRequest.getHeader("Accept-TimeZone");
        var result = getSdkProjectConfigByCodeUseCase.execute(code, timezone);
        var response = ProjectSettingsResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @GetMapping(value = "/user/qr")
    public ResponseEntity<ResponseApi<QrCodeResponseDTO>> getCreateUserQrCode() {
        UserContext ctx = UserContext.get();
        var result = getCreateUserQrCodeUseCase.execute(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var response = new QrCodeResponseDTO(result.base64QrCode(), result.link());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @PostMapping(value = "/user/token", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<UserResponseDTO>> createUserByToken(
            HttpServletRequest httpServletRequest,
            @ModelAttribute @Valid CreateUserByTokenRequestDTO request
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

    @GetMapping(value = "/verify/qr/{faceId}")
    public ResponseEntity<ResponseApi<QrCodeResponseDTO>> getVerifyQrCode(
            @PathVariable String faceId
    ) {
        UserContext ctx = UserContext.get();
        var result = getVerifyQrCodeUseCase.execute(ctx.getAccountIdAsLong(), ctx.getApiKey(), faceId);
        var response = new QrCodeResponseDTO(result.base64QrCode(), result.link());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @PostMapping(value = "/verify/token", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<VerifyByFaceIdResponseDTO>> verifyByToken(
            HttpServletRequest httpServletRequest,
            @ModelAttribute @Valid VerifyByTokenRequestDTO request
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

    @GetMapping(value = "/identify/qr")
    public ResponseEntity<ResponseApi<QrCodeResponseDTO>> getIdentifyQrCode() {
        UserContext ctx = UserContext.get();
        var result = getIdentifyQrCodeUseCase.execute(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var response = new QrCodeResponseDTO(result.base64QrCode(), result.link());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @PostMapping(value = "/identify/token", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<IdentifyResponseDTO>> identifyByToken(
            HttpServletRequest httpServletRequest,
            @ModelAttribute @Valid IdentifyByTokenRequestDTO request
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

    @GetMapping(value = "/liveness/qr")
    public ResponseEntity<ResponseApi<QrCodeResponseDTO>> getLivenessQrCode() {
        UserContext ctx = UserContext.get();
        var result = getLivenessQrCodeUserCase.execute(ctx.getAccountIdAsLong(), ctx.getApiKey());
        var response = new QrCodeResponseDTO(result.base64QrCode(), result.link());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @PostMapping(value = "/liveness/token", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<LivenessResponseDTO>> LivenessByToken(
            @ModelAttribute @Valid LivenessByTokenRequestDTO request
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

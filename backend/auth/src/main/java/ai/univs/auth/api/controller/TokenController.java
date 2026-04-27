package ai.univs.auth.api.controller;

import ai.univs.auth.api.dto.RepublishAccessTokenRequestDTO;
import ai.univs.auth.api.dto.RepublishAccessTokenResponseDTO;
import ai.univs.auth.api.dto.TokenValidationRequestDTO;
import ai.univs.auth.api.dto.TokenValidationResponseDTO;
import ai.univs.auth.application.exception.CustomAuthException;
import ai.univs.auth.application.service.JwtTokenProvider;
import ai.univs.auth.application.usecase.RepublishAccessTokenUseCase;
import ai.univs.auth.shared.swagger.SwaggerError;
import ai.univs.auth.shared.swagger.SwaggerErrorExample;
import ai.univs.auth.shared.web.dto.ResponseApi;
import ai.univs.auth.shared.web.enums.ErrorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "토큰")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/token")
public class TokenController {

    private final JwtTokenProvider jwtTokenProvider;
    private final RepublishAccessTokenUseCase republishAccessTokenUseCase;

    @Operation(summary = "Access Token 유효성 검증 (Gateway Server 전용)")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_ACCESS_TOKEN, status = 400),
            @SwaggerError(errorType = ErrorType.EXPIRATION_TOKEN, status = 401),
    })
    @PostMapping("/validate")
    public ResponseEntity<ResponseApi<TokenValidationResponseDTO>> validateToken(
            @Valid @RequestBody TokenValidationRequestDTO request
    ) {
        try {
            // Access Token 검사
            jwtTokenProvider.validateAccessToken(request.accessToken());

            // 페이로드 추출
            Long accountId = jwtTokenProvider.getAccountIdFromToken(request.accessToken());

            var response = TokenValidationResponseDTO.valid(accountId);
            return ResponseEntity.ok(ResponseApi.ok(response));
        } catch (CustomAuthException e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return ResponseEntity.ok(ResponseApi.ok(TokenValidationResponseDTO.invalid()));
        }
    }

    @Operation(summary = "Access Token 재발급")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_REFRESH_TOKEN, status = 400),
            @SwaggerError(errorType = ErrorType.EXPIRATION_TOKEN, status = 401),
    })
    @PostMapping("/refresh")
    public ResponseEntity<ResponseApi<RepublishAccessTokenResponseDTO>> republishAccessToken(
            @Valid @RequestBody RepublishAccessTokenRequestDTO request
    ) {
        var result = republishAccessTokenUseCase.execute(request.refreshToken());
        var response = RepublishAccessTokenResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

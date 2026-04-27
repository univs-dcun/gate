package ai.univs.auth.api.controller;

import ai.univs.auth.api.dto.*;
import ai.univs.auth.application.usecase.*;
import ai.univs.auth.shared.swagger.SwaggerError;
import ai.univs.auth.shared.swagger.SwaggerErrorExample;
import ai.univs.auth.shared.web.dto.ResponseApi;
import ai.univs.auth.shared.web.enums.ErrorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "가입 & 로그인")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final SendSignupCodeUseCase sendSignupCodeUseCase;
    private final VerifyEmailCodeUseCase verifyEmailCodeUseCase;
    private final SignupUseCase signupUseCase;
    private final LoginUseCase loginUseCase;
    private final LogoutUseCase logoutUseCase;

    @Operation(summary = "회원가입 인증 번호 요청")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.ALREADY_USE_EMAIL, status = 400),
    })
    @PostMapping("/signup/send-code")
    public ResponseEntity<ResponseApi<SendEmailVerificationCodeResponseDTO>> sendEmailVerificationCode(
            @RequestBody @Valid SendEmailVerificationCodeRequestDTO request
    ) {
        var result = sendSignupCodeUseCase.execute(request.email());
        var response = SendEmailVerificationCodeResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "회원가입 인증 번호 검증")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_FOUND_VERIFICATION, status = 400),
            @SwaggerError(errorType = ErrorType.EXPIRED_VERIFICATION, status = 400),
            @SwaggerError(errorType = ErrorType.TOO_MANY_ATTEMPTS_VERIFICATION, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_VERIFICATION_CODE, status = 400)
    })
    @PostMapping("/signup/verify-code")
    public ResponseEntity<ResponseApi<VerifyEmailCodeResponseDTO>> verifyEmailCode(
            @RequestBody @Valid VerifyEmailCodeRequestDTO request
    ) {
        var result = verifyEmailCodeUseCase.execute(request.email(), request.code());
        var response = new VerifyEmailCodeResponseDTO(result.verified());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "회원 가입")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_EMAIL_VERIFIED, status = 400),
            @SwaggerError(errorType = ErrorType.FAILED_CONFIRM_PASSWORD, status = 400),
            @SwaggerError(errorType = ErrorType.ALREADY_USE_EMAIL, status = 400),
    })
    @PostMapping("/signup")
    public ResponseEntity<ResponseApi<SignupResponseDTO>> signup(
            @Valid @RequestBody SignupRequestDTO request
    ) {
        var input = request.toSignupInput();
        var result = signupUseCase.execute(input);
        var response = SignupResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "로그인")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.FAILED_ACCOUNT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.FAILED_ACCOUNT_LOCKED, status = 400),
            @SwaggerError(errorType = ErrorType.FAILED_WRONG_PASSWORD, status = 400),
    })
    @PostMapping("/login")
    public ResponseEntity<ResponseApi<LoginResponseDTO>> login(
            @Valid @RequestBody LoginRequestDTO request
    ) {
        var result = loginUseCase.execute(request.email(), request.password());
        var response = LoginResponseDTO.of(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "로그아웃")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_REFRESH_TOKEN, status = 400),
    })
    @PostMapping("/logout")
    public ResponseEntity<ResponseApi<Void>> logout(@Valid @RequestBody LogoutRequestDTO request) {
        logoutUseCase.execute(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}

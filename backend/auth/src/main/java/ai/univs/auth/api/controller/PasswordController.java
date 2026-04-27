package ai.univs.auth.api.controller;

import ai.univs.auth.api.dto.*;
import ai.univs.auth.application.usecase.PasswordChangeUseCase;
import ai.univs.auth.application.usecase.PasswordResetUseCase;
import ai.univs.auth.application.usecase.SendResetPasswordCodeUseCase;
import ai.univs.auth.application.usecase.VerifyResetPasswordCodeUseCase;
import ai.univs.auth.shared.swagger.SwaggerError;
import ai.univs.auth.shared.swagger.SwaggerErrorExample;
import ai.univs.auth.shared.web.dto.ResponseApi;
import ai.univs.auth.shared.web.enums.ErrorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "비밀번호")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/password")
public class PasswordController {

    private final PasswordChangeUseCase passwordChangeUseCase;
    private final SendResetPasswordCodeUseCase sendResetPasswordCodeUseCase;
    private final VerifyResetPasswordCodeUseCase verifyResetPasswordCodeUseCase;
    private final PasswordResetUseCase passwordResetUseCase;

    @Operation(summary = "비밀번호 변경 (세션 있을 때)")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.FAILED_ACCOUNT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.FAILED_WRONG_PASSWORD, status = 400),
            @SwaggerError(errorType = ErrorType.ALREADY_USED_PASSWORD, status = 400),
    })
    @PutMapping("/change")
    public ResponseEntity<ResponseApi<PasswordChangeResponseDTO>> passwordChange(
            @Valid @RequestBody PasswordChangeRequestDTO request
    ) {
        var input = request.toPasswordChangeInput();
        var result = passwordChangeUseCase.execute(input);
        var response = new PasswordChangeResponseDTO(result.AccountId());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "비밀번호 재설정 인증번호 발송 (세션 없을 때)")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.FAILED_ACCOUNT_NOT_FOUND, status = 400),
    })
    @PostMapping("/reset/send-code")
    public ResponseEntity<ResponseApi<Void>> requestPasswordReset(
            @Valid @RequestBody RequestPasswordResetRequestDTO request
    ) {
        sendResetPasswordCodeUseCase.execute(request.email());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "비밀번호 재설정 인증번호 검증 (세션 없을 때)")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_FOUND_VERIFICATION, status = 400),
            @SwaggerError(errorType = ErrorType.EXPIRED_VERIFICATION, status = 400),
            @SwaggerError(errorType = ErrorType.TOO_MANY_ATTEMPTS_VERIFICATION, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_VERIFICATION_CODE, status = 400)
    })
    @PostMapping("/reset/verify-code")
    public ResponseEntity<ResponseApi<ResetPasswordCodeResponseDTO>> verifyResetPasswordCode(
            @RequestBody @Valid VerifyResetPasswordCodeRequestDTO request
    ) {
        var result = verifyResetPasswordCodeUseCase.execute(request.email(), request.code());
        var response = new ResetPasswordCodeResponseDTO(result.verified());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "비밀번호 재설정 (세션 없을 때)")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.FAILED_ACCOUNT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_FOUND_VERIFICATION, status = 400),
            @SwaggerError(errorType = ErrorType.EXPIRED_VERIFICATION, status = 400),
            @SwaggerError(errorType = ErrorType.TOO_MANY_ATTEMPTS_VERIFICATION, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_VERIFICATION_CODE, status = 400),
            @SwaggerError(errorType = ErrorType.ALREADY_USED_PASSWORD, status = 400),
    })
    @PostMapping("/reset")
    public ResponseEntity<ResponseApi<Void>> resetPassword(
            @Valid @RequestBody PasswordResetConfirmDTO request
    ) {
        passwordResetUseCase.execute(
                request.email(),
                request.newPassword(),
                request.passwordConfirm());
        return ResponseEntity.noContent().build();
    }
}

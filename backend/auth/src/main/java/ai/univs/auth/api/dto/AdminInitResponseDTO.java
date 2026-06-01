package ai.univs.auth.api.dto;

import ai.univs.auth.application.result.SignupResult;

import java.time.LocalDateTime;

public record AdminInitResponseDTO(
        Long accountId,
        String email,
        LocalDateTime createdAt
) {
    public static AdminInitResponseDTO from(SignupResult result) {
        return new AdminInitResponseDTO(result.accountId(), result.email(), result.createdAt());
    }
}

package ai.univs.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminInitRequestDTO(
        @NotBlank(message = "REQUIRED_EMAIL")
        @Email(message = "INVALID_EMAIL_FORMAT")
        @Size(max = 255, message = "INVALID_EMAIL_LENGTH")
        String email,

        @NotBlank(message = "REQUIRED_PASSWORD")
        @Size(min = 8, max = 20, message = "INVALID_PASSWORD_LENGTH")
        String password
) {}

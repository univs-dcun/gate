package ai.univs.palm.api.dto;

import ai.univs.palm.application.input.RegisterBranchInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record RegisterBranchRequestDTO(
        @Schema(description = "Watchlist 표시 이름 (max 200)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_DISPLAY_NAME")
        @Length(max = 200, message = "INVALID_DISPLAY_NAME_LENGTH")
        String displayName,

        @Schema(description = "Watchlist 전체 이름 (max 200)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_FULL_NAME")
        @Length(max = 200, message = "INVALID_FULL_NAME_LENGTH")
        String fullName
) {

    public RegisterBranchInput toInput() {
        return new RegisterBranchInput(displayName, fullName);
    }
}

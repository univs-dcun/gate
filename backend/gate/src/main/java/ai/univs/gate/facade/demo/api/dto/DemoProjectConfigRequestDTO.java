package ai.univs.gate.facade.demo.api.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record DemoProjectConfigRequestDTO(
        @Schema(description = SwaggerDescriptions.API_KEY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_API_KEY")
        @Length(max = 36, message = "INVALID_API_KEY_LENGTH")
        String apiKey
) {
}

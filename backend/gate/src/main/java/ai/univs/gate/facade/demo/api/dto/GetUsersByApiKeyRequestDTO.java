package ai.univs.gate.facade.demo.api.dto;

import ai.univs.gate.facade.demo.application.input.GetUsersByApiKeyInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record GetUsersByApiKeyRequestDTO(
        @Schema(description = SwaggerDescriptions.API_KEY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_API_KEY")
        @Length(max = 36, message = "INVALID_API_KEY_LENGTH")
        String apiKey,

        @Schema(description = SwaggerDescriptions.USER_KEYWORD)
        String userKeyword,

        @Schema(description = SwaggerDescriptions.PAGE, defaultValue = "1")
        @Min(value = 1, message = "INVALID_PAGE_COUNT")
        @Max(value = 1000, message = "INVALID_PAGE_COUNT")
        Integer page,

        @Schema(description = SwaggerDescriptions.PAGE_SIZE, defaultValue = "10")
        @Min(value = 1, message = "INVALID_PAGE_COUNT")
        @Max(value = 1000, message = "INVALID_PAGE_COUNT")
        Integer pageSize,

        @Schema(description = SwaggerDescriptions.IS_DELETED)
        Boolean isDeleted,

        @Schema(description = SwaggerDescriptions.SELECT_START_DATE)
        String startDate,

        @Schema(description = SwaggerDescriptions.SELECT_END_DATE)
        String endDate
) {

    public GetUsersByApiKeyInput toInput(String timezone) {
        return new GetUsersByApiKeyInput(
                apiKey,
                userKeyword,
                page != null ? page : 1,
                pageSize != null ? pageSize : 10,
                isDeleted,
                startDate,
                endDate,
                timezone
        );
    }
}

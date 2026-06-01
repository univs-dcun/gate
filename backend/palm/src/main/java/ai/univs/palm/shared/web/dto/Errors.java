package ai.univs.palm.shared.web.dto;

import ai.univs.palm.shared.swagger.SwaggerDescriptions;
import ai.univs.palm.shared.web.enums.ErrorType;
import io.swagger.v3.oas.annotations.media.Schema;

public record Errors(
        @Schema(description = SwaggerDescriptions.RESPONSE_ERROR_CODE)
        String code,
        @Schema(description = SwaggerDescriptions.RESPONSE_ERROR_TYPE)
        String type,
        @Schema(description = SwaggerDescriptions.RESPONSE_ERROR_MESSAGE)
        String message
) {

    public static Errors from(ErrorType errorType, String message) {
        return new Errors(errorType.getCode(), errorType.name(), message);
    }
}

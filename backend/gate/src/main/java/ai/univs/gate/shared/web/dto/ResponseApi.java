package ai.univs.gate.shared.web.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record ResponseApi<T>(
        @Schema(description = SwaggerDescriptions.RESPONSE_SUCCESS)
        boolean success,
        @Schema(description = SwaggerDescriptions.RESPONSE_DATA)
        T data,
        @Schema(description = SwaggerDescriptions.ERRORS)
        Errors errors
) {

    public static <T> ResponseApi<T> ok(T data) {
        return new ResponseApi<>(true, data, null);
    }

    public static <T> ResponseApi<T> fail(Errors errors) {
        return new ResponseApi<>(false, null, errors);
    }
}

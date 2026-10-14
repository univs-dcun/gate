package ai.univs.gate.shared.web.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.web.enums.ErrorType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record Errors(
        @Schema(description = SwaggerDescriptions.RESPONSE_ERROR_CODE)
        String code,

        @Schema(description = SwaggerDescriptions.RESPONSE_ERROR_TYPE)
        String type,

        @Schema(description = SwaggerDescriptions.RESPONSE_ERROR_MESSAGE)
        String message,

        /** 오류마다 덧붙이는 값. 없으면 응답에서 빠진다 — 예: MATCH-003 의 {@code featureId}(겹친 특징점) (UG-378) */
        @Schema(description = SwaggerDescriptions.RESPONSE_ERROR_DETAILS)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Map<String, Object> details
) {

    public Errors(String code, String type, String message) {
        this(code, type, message, null);
    }

    public static Errors from(ErrorType errorType, String message) {
        return new Errors(errorType.getCode(), errorType.name(), message);
    }
}

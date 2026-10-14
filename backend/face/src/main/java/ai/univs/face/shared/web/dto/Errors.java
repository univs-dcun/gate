package ai.univs.face.shared.web.dto;

import ai.univs.face.shared.swagger.SwaggerDescriptions;
import ai.univs.face.shared.web.enums.ErrorType;
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
        /** 하위 서비스가 덧붙인 값을 그대로 넘긴다. 없으면 응답에서 빠진다 — 예: MATCH-003 의 {@code faceId} (UG-378) */
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

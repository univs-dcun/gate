package ai.univs.palm.shared.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorType {

    // Swagger
    UNAUTHORIZED("SWAGGER-001", HttpStatus.UNAUTHORIZED),
    NEED_SERVICE_ROLE("SWAGGER-002", HttpStatus.FORBIDDEN),
    NOT_FOUND("SWAGGER-003", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("SWAGGER-004", HttpStatus.METHOD_NOT_ALLOWED),
    INTERNAL_SERVER_ERROR("SWAGGER-005", HttpStatus.INTERNAL_SERVER_ERROR),

    // Common
    INVALID_INPUT("COMMON-001", HttpStatus.BAD_REQUEST),

    // Palm
    NOT_PALM_IMAGE("PALM-001", HttpStatus.BAD_REQUEST),
    NOT_MATCH("PALM-003", HttpStatus.BAD_REQUEST),
    MISMATCH("PALM-004", HttpStatus.BAD_REQUEST),
    NO_DOUBLE_SIMILARITY("PALM-005", HttpStatus.BAD_REQUEST),
    PALM_NOT_FOUND("PALM-008", HttpStatus.BAD_REQUEST),
    ;

    // 메시지는 다국어 지원으로 messages.properties 통해서 제공
    private final String code;       // 시스템 관리용 코드
    private final HttpStatus status; // HTTP 응답 상태

    public static ErrorType from(String errorTypeString) {
        for (ErrorType type : values()) {
            if (type.name().equals(errorTypeString)) {
                return type;
            }
        }

        return INTERNAL_SERVER_ERROR;
    }
}

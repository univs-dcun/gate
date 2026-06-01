package ai.univs.auth.shared.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorType {

    // Swagger
    UNAUTHORIZED("PJ-001", HttpStatus.UNAUTHORIZED),
    NEED_SERVICE_ROLE("PJ-002", HttpStatus.FORBIDDEN),
    NOT_FOUND("PJ-003", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("PJ-004", HttpStatus.METHOD_NOT_ALLOWED),
    INTERNAL_SERVER_ERROR("PJ-005", HttpStatus.INTERNAL_SERVER_ERROR),

    // Common
    INVALID_INPUT("PJ-101", HttpStatus.BAD_REQUEST),

    // Auth
    ALREADY_USE_EMAIL("AUTH-101", HttpStatus.BAD_REQUEST),
    RETRY_USER_AUTHENTICATION("AUTH-102", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_PERMISSION("AUTH-103", HttpStatus.BAD_REQUEST),
    EXPIRATION_TOKEN("AUTH-104", HttpStatus.BAD_REQUEST),
    EXPIRATION_REFRESH_TOKEN("AUTH-105", HttpStatus.BAD_REQUEST),
    INVALID_ACCESS_TOKEN("AUTH-106", HttpStatus.BAD_REQUEST),
    INVALID_REFRESH_TOKEN("AUTH-107", HttpStatus.BAD_REQUEST),
    INVALID_TOKEN_TYPE("AUTH-108", HttpStatus.BAD_REQUEST),
    INVALID_REFRESH_TOKEN_TYPE("AUTH-109", HttpStatus.BAD_REQUEST),
    FAILED_WRONG_PASSWORD("AUTH-110", HttpStatus.BAD_REQUEST),
    FAILED_ACCOUNT_LOCKED("AUTH-111", HttpStatus.BAD_REQUEST),
    FAILED_ACCOUNT_NOT_FOUND("AUTH-112", HttpStatus.BAD_REQUEST),
    FAILED_CONFIRM_PASSWORD("AUTH-113", HttpStatus.BAD_REQUEST),
    ALREADY_USED_PASSWORD_RESET_TOKEN("AUTH-114", HttpStatus.BAD_REQUEST),
    ALREADY_USED_PASSWORD("AUTH-115", HttpStatus.BAD_REQUEST),
    SUCCESS_PASSWORD_CHANGE("AUTH-116", HttpStatus.BAD_REQUEST),
    NOT_FOUND_VERIFICATION("AUTH-117", HttpStatus.BAD_REQUEST),
    EXPIRED_VERIFICATION("AUTH-118", HttpStatus.BAD_REQUEST),
    TOO_MANY_ATTEMPTS_VERIFICATION("AUTH-119", HttpStatus.BAD_REQUEST),
    INVALID_VERIFICATION_CODE("AUTH-120", HttpStatus.BAD_REQUEST),
    SUCCESS_SEND_EMAIL_CODE("AUTH-121", HttpStatus.BAD_REQUEST),
    SUCCESS_VERIFY_EMAIL("AUTH-122", HttpStatus.BAD_REQUEST),
    NOT_EMAIL_VERIFIED("AUTH-123", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_LENGTH("AUTH-124", HttpStatus.BAD_REQUEST),
    REQUIRED_EMAIL("AUTH-125", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_FORMAT("AUTH-126", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD_LENGTH("AUTH-127", HttpStatus.BAD_REQUEST),
    REQUIRED_PASSWORD("AUTH-128", HttpStatus.BAD_REQUEST),
    ADMIN_ALREADY_INITIALIZED("AUTH-129", HttpStatus.CONFLICT);

    private final String code;
    private final HttpStatus status;

    public static ErrorType from(String errorTypeString) {
        for (ErrorType type : values()) {
            if (type.name().equals(errorTypeString)) {
                return type;
            }
        }
        return INTERNAL_SERVER_ERROR;
    }
}

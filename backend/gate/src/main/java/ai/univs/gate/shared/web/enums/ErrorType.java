package ai.univs.gate.shared.web.enums;

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
    INVALID_PAGE_COUNT("CMMN-101", HttpStatus.BAD_REQUEST),
    INVALID_TRANSACTION_UUID_LENGTH("CMMN-102", HttpStatus.BAD_REQUEST),
    REQUIRED_TRANSACTION_UUID("CMMN-103", HttpStatus.BAD_REQUEST),

    // JWT
    EXPIRATION_TOKEN("AUTH-104", HttpStatus.BAD_REQUEST),
    INVALID_TOKEN("AUTH-106", HttpStatus.BAD_REQUEST),

    // Project
    INVALID_INPUT("PJ-101", HttpStatus.BAD_REQUEST),
    PROJECT_ALREADY_EXISTS("PJ-102", HttpStatus.BAD_REQUEST),
    PROJECT_NOT_FOUND("PJ-103", HttpStatus.BAD_REQUEST),
    NOT_OWNERSHIP("PJ-104", HttpStatus.FORBIDDEN),
    API_KEY_NOT_FOUND("PJ-105", HttpStatus.BAD_REQUEST),
    SETTINGS_NOT_FOUND("PJ-106", HttpStatus.BAD_REQUEST),
    COMPANY_ALREADY_EXISTS("PJ-107", HttpStatus.BAD_REQUEST),
    COMPANY_NOT_FOUND("PJ-108", HttpStatus.BAD_REQUEST),
    PROJECT_LIMIT_EXCEEDED("PJ-109", HttpStatus.BAD_REQUEST),
    WEBHOOK_CONFIG_NOT_FOUND("PJ-110", HttpStatus.BAD_REQUEST),

    // User
    INVALID_USER("USER-101", HttpStatus.BAD_REQUEST),

    // File
    INVALID_FILE("FILE-101", HttpStatus.BAD_REQUEST),
    INVALID_FILE_PATH("FILE-102", HttpStatus.BAD_REQUEST),
    REQUIRED_FILE_NAME("FILE-103", HttpStatus.BAD_REQUEST),
    REQUIRED_EXTENSION("FILE-104", HttpStatus.BAD_REQUEST),
    FAILURE_COMPRESSION_FILE("FILE-105", HttpStatus.BAD_REQUEST),

    // Mail

    // match
    NOT_FOUND_MATCHING_HISTORY("ID-101", HttpStatus.BAD_REQUEST),
    NOT_MATCH("ID-102", HttpStatus.BAD_REQUEST),
    MISMATCH("ID-103", HttpStatus.BAD_REQUEST),
        // Liveness
    FACE_NOT_FOUND("ID-201", HttpStatus.BAD_REQUEST),
    TOO_MANY_FACES("ID-202", HttpStatus.BAD_REQUEST),
    SMALL_FACE_SIZE("ID-203", HttpStatus.BAD_REQUEST),
    SMALL_RELATIVE_FACE_SIZE("ID-204", HttpStatus.BAD_REQUEST),
    SMALL_PUPILLARY_DISTANCE("ID-205", HttpStatus.BAD_REQUEST),
    LARGE_FACE_ROTATION_ANGLE("ID-206", HttpStatus.BAD_REQUEST),
    FACE_TOO_CLOSE("ID-207", HttpStatus.BAD_REQUEST),
    FACE_CLOSE_TO_BORDER("ID-208", HttpStatus.BAD_REQUEST),
    FACE_CROPPED("ID-209", HttpStatus.BAD_REQUEST),
    FACE_OCCLUDED("ID-210", HttpStatus.BAD_REQUEST),
    EYES_CLOSED("ID-211", HttpStatus.BAD_REQUEST),
    DARK_IMAGE("ID-222", HttpStatus.BAD_REQUEST),
    FAKE("ID-223", HttpStatus.BAD_REQUEST),
    FACE_IS_OCCLUDED("ID-224", HttpStatus.BAD_REQUEST),
    FACE_TOO_SMALL("ID-225", HttpStatus.BAD_REQUEST),
    FACE_ANGLE_TOO_LARGE("ID-226", HttpStatus.BAD_REQUEST),

    // Demo
    INVALID_QR_CODE("DEMO-101", HttpStatus.BAD_REQUEST),
    EXPIRED_QR_CODE("DEMO-102", HttpStatus.BAD_REQUEST),

    // Billing - 구독 & 플랜
    PLAN_NOT_FOUND("BIL-101", HttpStatus.NOT_FOUND),
    SUBSCRIPTION_NOT_FOUND("BIL-102", HttpStatus.NOT_FOUND),
    SUBSCRIPTION_ALREADY_EXISTS("BIL-103", HttpStatus.BAD_REQUEST),

    // Billing - 크레딧 & 한도
    CREDIT_BALANCE_NOT_FOUND("BIL-201", HttpStatus.NOT_FOUND),
    FEATURE_LIMIT_NOT_FOUND("BIL-202", HttpStatus.NOT_FOUND),
    INSUFFICIENT_PLAN_CREDIT("BIL-203", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_FLEX_CREDIT("BIL-204", HttpStatus.BAD_REQUEST),
    FEATURE_LIMIT_EXCEEDED("BIL-205", HttpStatus.PAYMENT_REQUIRED),
    TOTAL_CREDIT_LIMIT_EXCEEDED("BIL-206", HttpStatus.BAD_REQUEST),
    INVALID_REALLOCATION_AMOUNT("BIL-207", HttpStatus.BAD_REQUEST),

    // Billing - DB 저장 용량
    DB_STORAGE_LIMIT_EXCEEDED("BIL-301", HttpStatus.PAYMENT_REQUIRED),

    // Billing - Flex 충전
    INVALID_FLEX_CHARGE_AMOUNT("BIL-401", HttpStatus.BAD_REQUEST),
    FLEX_CHARGE_ORDER_NOT_FOUND("BIL-402", HttpStatus.NOT_FOUND),
    FLEX_CHARGE_ALREADY_PROCESSED("BIL-403", HttpStatus.BAD_REQUEST),
    PORTONE_PAYMENT_VERIFICATION_FAILED("BIL-404", HttpStatus.BAD_REQUEST),

    // Billing - 프로젝트 플랜 정책
    FREE_PLAN_PROJECT_LIMIT_EXCEEDED("BIL-501", HttpStatus.BAD_REQUEST),
    PROJECT_HAS_ACTIVE_SUBSCRIPTION("BIL-502", HttpStatus.BAD_REQUEST),

    PACKAGE_KEY_NOT_ALLOWED("PJ-111", HttpStatus.BAD_REQUEST),
    PROJECT_MODULE_TYPE_IMMUTABLE("PJ-112", HttpStatus.BAD_REQUEST),
    UNSUPPORTED_MODULE_TYPE("PJ-113", HttpStatus.BAD_REQUEST),
    DEMO_DISABLED("PJ-114", HttpStatus.FORBIDDEN),
    SDK_DISABLED("PJ-115", HttpStatus.FORBIDDEN);

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
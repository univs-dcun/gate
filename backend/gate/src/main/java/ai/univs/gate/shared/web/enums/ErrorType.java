package ai.univs.gate.shared.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 오류 코드와 <b>오류의 성격</b>.
 *
 * <p><b>{@code status} 는 응답 상태 코드가 아니다</b> (UG-298). {@code GlobalExceptionHandler} 의
 * {@code handleBusinessException} 이 {@code @ResponseStatus(BAD_REQUEST)} 고정이라,
 * {@code BusinessException} 계열은 여기 무엇을 적든 클라이언트가 <b>항상 400 을 받는다.</b>
 *
 * <p>대부분은 우연히 맞는다 — 47개 중 39개가 {@code BAD_REQUEST} 이고, 프레임워크 예외를 받는
 * 네 핸들러({@code NOT_FOUND}·{@code METHOD_NOT_ALLOWED} ×2·{@code INTERNAL_SERVER_ERROR})는
 * 자기 {@code @ResponseStatus} 와 일치한다. 실제로 어긋나는 것은 {@code UNAUTHORIZED}(401)와
 * {@code NOT_OWNERSHIP}(403) 둘뿐이다. (초판 주석은 "일치한 적이 한 번도 없다" 고 적었는데
 * 그 둘에서 과일반화한 것이었다 — 리뷰 지적.)
 *
 * <p>지금 이 값이 결정하는 것은 <b>로그 수준</b> 하나다 (UG-290).
 *
 * <ul>
 *   <li>4xx — 원인이 클라이언트 입력이다. WARN, 스택트레이스 없음.
 *   <li>5xx — 원인이 우리 쪽이다. ERROR + 스택트레이스.
 * </ul>
 *
 * <p>그러므로 새 값을 정할 때 물어야 할 것은 "HTTP 로 치면 몇 번인가" 가 아니라
 * <b>"이 오류가 났을 때 우리가 무언가 고쳐야 하는가"</b> 다.
 *
 * <p>UG-290 은 "값이 다 채워져 있는데 한 번도 읽히지 않던 죽은 코드였으니 살려 쓰자" 는 논리로
 * 이 필드를 되살렸다. 앞부분은 사실이었지만 <b>거기서 "그러므로 정확하다" 가 따라 나오지
 * 않는다</b> — 한 번도 실행되지 않았기 때문에 정확할 이유가 없었다. UG-298 에서 전수 점검했고,
 * 그 결과를 {@code ErrorTypeClassificationTest} 가 못박는다.
 */
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
    /**
     * <b>원인이 섞여 있다</b> — 세 번째 사례다 (UG-298 리뷰가 찾았다. 초판은 두 개라고 적었다).
     *
     * <p>클라이언트 입력: 없는 키, 남의 키, 삭제된 프로젝트의 키. 이 셋을 <b>같은 코드로</b>
     * 막는 것이 이 상수의 핵심 목적이다 — 구분해 주면 키의 실재를 확인해 주는 열거 오라클이
     * 된다 ({@code ApiKeyService} 참고).
     *
     * <p>우리 쪽 문제: {@code GetApiKeyUseCase} 의 {@code findLatestActiveByProjectId().orElseThrow}.
     * (UG-302 때는 {@code RegenerateApiKeyUseCase} 도 같은 자리였는데 UG-312 에서 제거됐다.)
     * {@code validateOwnership} 을
     * 먼저 거치므로, 도달했다는 것은 "소유가 확인된, 삭제되지 않은 프로젝트에 활성 키가 없다"
     * 는 뜻이다 — {@link #SETTINGS_NOT_FOUND} 와 완전히 같은 논리다.
     *
     * <p><b>그런데도 4xx 로 둔다.</b> 열거 오라클 방지가 우선이라 코드를 나눌 수 없다. 대신
     * 그 두 자리에서 직접 {@code log.error} 를 남긴다 (리뷰 지적 — 그 전까지는 아무것도 남기지
     * 않아 WARN 한 줄로 조용히 지나갔다. 섞인 코드의 대가를 치르는 방식이 다른 두 코드와
     * 달랐던 셈이다).
     */
    API_KEY_NOT_FOUND("PJ-105", HttpStatus.BAD_REQUEST),
    /**
     * 프로젝트에 설정 행이 없다.
     *
     * <p><b>클라이언트 입력 문제가 아니다</b> (UG-298 재분류). 이 예외를 던지는 여섯 자리는
     * 모두 프로젝트를 먼저 해결한 뒤 설정을 찾는다 — 즉 "존재하는 프로젝트에 설정이 없다" 는
     * 뜻이고, {@code CreateProjectUseCase} 가 생성 시 항상 설정을 함께 저장하므로 그런 행은
     * 있을 수 없다. 있다면 데이터가 깨진 것이고 우리가 고쳐야 한다.
     *
     * <p>4xx 로 두면 WARN 으로 조용히 지나가 아무도 모른다. 응답은 그대로 PJ-106 / 400 이다 —
     * 바뀌는 것은 로그 수준뿐이다.
     */
    SETTINGS_NOT_FOUND("PJ-106", HttpStatus.INTERNAL_SERVER_ERROR),
    COMPANY_ALREADY_EXISTS("PJ-107", HttpStatus.BAD_REQUEST),
    PROJECT_LIMIT_EXCEEDED("PJ-109", HttpStatus.BAD_REQUEST),
    WEBHOOK_CONFIG_NOT_FOUND("PJ-110", HttpStatus.BAD_REQUEST),

    // User
    INVALID_USER("USER-101", HttpStatus.BAD_REQUEST),

    // File
    INVALID_FILE("FILE-101", HttpStatus.BAD_REQUEST),
    /**
     * <b>원인이 섞여 있다</b> (UG-298). 4xx 로 둔 것은 잠정이다.
     *
     * <p>클라이언트 입력: {@code FileController.validateFilePath}(경로 순회 시도).
     *
     * <p>애매한 것: {@code FileService.validationFilePath}(빈 경로). {@code down} 에서는
     * 클라이언트가 준 값이지만 {@code delete} 에서는 <b>우리 DB 에 저장된 특징점 이미지 경로</b>가
     * 온다 — 그쪽이 비어 있다면 우리 문제다 (리뷰 지적).
     *
     * <p>우리 쪽 문제: {@code FileUtil.getFile}·{@code delete} 의 IO 실패. 그 경로는 우리 DB 에
     * 저장된 값이므로, 읽지 못한다는 것은 파일이 사라졌거나 볼륨이 안 붙었거나
     * {@code file.root-path} 가 어긋났다는 뜻이다 — 온프레미스에서 흔하다.
     *
     * <p>둘을 가르려면 코드를 나눠야 하는데 그건 클라이언트 계약 변경이다. 그때까지는 두 서버
     * 경로가 {@code log.error} + 스택트레이스를 직접 남긴다.
     */
    INVALID_FILE_PATH("FILE-102", HttpStatus.BAD_REQUEST),
    REQUIRED_FILE_NAME("FILE-103", HttpStatus.BAD_REQUEST),
    REQUIRED_EXTENSION("FILE-104", HttpStatus.BAD_REQUEST),
    /**
     * <b>원인이 섞여 있다</b> (UG-298). 4xx 로 둔 것은 잠정이다.
     *
     * <p>클라이언트 입력: {@code ImageIO.read} 실패(깨진 이미지).
     *
     * <p>우리 쪽 문제: 리사이즈 후 {@code ImageIO.write} 실패 — 디스크 풀, 권한 없음,
     * {@code file.root-path} 오설정.
     *
     * <p>{@code FileUtil.fileResizeAndSave} 가 두 블록을 나눠 각각 WARN·ERROR 로 남긴다
     * (UG-290). 코드 분리는 클라이언트 계약 변경이라 별도 판단이 필요하다.
     */
    FAILURE_COMPRESSION_FILE("FILE-105", HttpStatus.BAD_REQUEST),

    // Mail

    // match
    NOT_FOUND_MATCHING_HISTORY("ID-101", HttpStatus.BAD_REQUEST),
    /**
     * <b>예외로 던지지 않는다</b> (UG-295·UG-298 확인). 1:N 매칭에서 후보를 못 찾았을 때
     * {@code matchHistory.fail(..., NOT_MATCH.name())} 로 <b>이력의 실패 사유 문자열</b>로만
     * 쓰인다. 응답은 HTTP 200 이고 {@code failureReason} 에 i18n 메시지가 실린다.
     *
     * <p>그래서 {@code @SwaggerError} 로 선언하면 안 된다 — UG-295 에서 PalmController 의
     * 잘못된 선언을 지웠다. 상수를 남겨 두는 이유는 이력 문자열과 messages.properties 키가
     * 이 이름에 묶여 있기 때문이다.
     */
    NOT_MATCH("ID-102", HttpStatus.BAD_REQUEST),
    /**
     * {@link #NOT_MATCH} 와 같다 — <b>예외로 던지지 않는다.</b> 1:1 검증에서 임계값에 못 미쳤을 때
     * {@code matchHistory.fail(..., MISMATCH.name())} 로 이력의 실패 사유 문자열로만 쓰인다
     * (1:N 은 {@code NOT_MATCH}, 1:1 은 이것 — 섞이면 운영자가 구분하지 못한다).
     */
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

    // Palm
    ALREADY_REGISTERED_PALM_FEATURE("PALM-101", HttpStatus.BAD_REQUEST),

    // Demo
    INVALID_QR_CODE("DEMO-101", HttpStatus.BAD_REQUEST),
    EXPIRED_QR_CODE("DEMO-102", HttpStatus.BAD_REQUEST);

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
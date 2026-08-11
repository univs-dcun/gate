package ai.univs.gate.shared.web.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * UG-298: {@code ErrorType.status} 전수 분류를 못박는다.
 *
 * <p>UG-290 이 이 필드를 로그 수준 판정 기준으로 쓰기 시작했다. 그 전까지는 프로덕션에서 한 번도
 * 읽히지 않는 죽은 값이었고, <b>한 번도 실행되지 않았으므로 정확할 이유가 없었다.</b>
 *
 * <p>이제는 4xx 로 잘못 라벨링된 서버 오류가 WARN 으로 조용히 지나간다. 에러 대시보드를 붙이면
 * 그대로 누락된다. 그래서 분류를 값이 아니라 <b>목록</b>으로 못박는다 — 새 상수를 추가하거나
 * 기존 분류를 바꾸면 여기서 걸리고, 고치는 사람이 의도를 밝혀야 한다.
 *
 * <p>판정 기준은 "HTTP 로 치면 몇 번인가" 가 아니다 (어차피 응답은 전부 400 이다).
 * <b>"이 오류가 났을 때 우리가 무언가 고쳐야 하는가"</b> 다.
 */
@DisplayName("UG-298: ErrorType 분류")
class ErrorTypeClassificationTest {

    /**
     * 우리 쪽 문제로 분류한 것 — ERROR + 스택트레이스로 남는다.
     *
     * <p>{@code SETTINGS_NOT_FOUND} 가 UG-298 에서 새로 들어왔다. 이 예외를 던지는 여섯 자리는
     * 모두 프로젝트를 먼저 해결한 뒤 설정을 찾으므로, 도달했다는 것은 "존재하는 프로젝트에
     * 설정 행이 없다" 는 뜻이다. {@code CreateProjectUseCase} 가 생성 시 항상 함께 저장하니
     * 그런 행은 있을 수 없고, 있다면 데이터가 깨진 것이다.
     */
    private static final Set<ErrorType> 우리_쪽_문제 = Set.of(
            ErrorType.INTERNAL_SERVER_ERROR,
            ErrorType.SETTINGS_NOT_FOUND);

    /**
     * <b>전수 목록.</b> 새 상수를 추가하면 여기서 걸린다.
     *
     * <p>처음에는 5xx 집합만 고정했는데, 리뷰가 그것이 <b>정확히 반대 방향으로 뚫려 있음</b>을
     * 변이로 보여 줬다 — 4xx 상수를 새로 추가하면 337개가 전부 초록이었다. 이 티켓이 존재하는
     * 이유가 "서버 원인 오류에 반사적으로 4xx 를 달아 WARN 으로 조용히 지나간다" 인데, 가드가
     * 잡는 쪽은 시끄럽고 무해한 방향(잘못된 5xx)뿐이고 조용하고 해로운 방향은 놓치고 있었다.
     *
     * <p>그래서 이름 → 분류를 전부 적는다. 상수를 하나 추가하는 사람은 여기에도 한 줄을
     * 적어야 하고, 그 순간 "이건 클라이언트 잘못인가 우리 잘못인가" 를 한 번은 생각하게 된다.
     * 그게 이 가드가 실제로 하려는 일이다.
     */
    private static final Set<String> 전체_상수 = Set.of(
            // Swagger 공통 — 프레임워크 예외 핸들러가 쓴다
            "UNAUTHORIZED", "NEED_SERVICE_ROLE", "NOT_FOUND", "METHOD_NOT_ALLOWED",
            "INTERNAL_SERVER_ERROR",
            // Common
            "INVALID_PAGE_COUNT", "INVALID_TRANSACTION_UUID_LENGTH", "REQUIRED_TRANSACTION_UUID",
            // JWT
            "EXPIRATION_TOKEN", "INVALID_TOKEN",
            // Project
            "INVALID_INPUT", "PROJECT_ALREADY_EXISTS", "PROJECT_NOT_FOUND", "NOT_OWNERSHIP",
            "API_KEY_NOT_FOUND", "SETTINGS_NOT_FOUND", "COMPANY_ALREADY_EXISTS",
            "PROJECT_LIMIT_EXCEEDED", "WEBHOOK_CONFIG_NOT_FOUND",
            // User / File
            "INVALID_USER",
            "INVALID_FILE", "INVALID_FILE_PATH", "REQUIRED_FILE_NAME", "REQUIRED_EXTENSION",
            "FAILURE_COMPRESSION_FILE",
            // match — NOT_MATCH·MISMATCH 는 예외가 아니라 이력 실패 사유 문자열이다
            "NOT_FOUND_MATCHING_HISTORY", "NOT_MATCH", "MISMATCH",
            // Liveness — LivenessErrorType 을 그대로 미러링한다
            "FACE_NOT_FOUND", "TOO_MANY_FACES", "SMALL_FACE_SIZE", "SMALL_RELATIVE_FACE_SIZE",
            "SMALL_PUPILLARY_DISTANCE", "LARGE_FACE_ROTATION_ANGLE", "FACE_TOO_CLOSE",
            "FACE_CLOSE_TO_BORDER", "FACE_CROPPED", "FACE_OCCLUDED", "EYES_CLOSED",
            "DARK_IMAGE", "FAKE", "FACE_IS_OCCLUDED", "FACE_TOO_SMALL", "FACE_ANGLE_TOO_LARGE",
            // Palm / Demo
            "ALREADY_REGISTERED_PALM_FEATURE",
            "INVALID_QR_CODE", "EXPIRED_QR_CODE");

    @Test
    @DisplayName("상수 목록이 고정돼 있다 — 추가·삭제하면 분류를 다시 판단해야 한다")
    void 전체_목록이_고정돼_있다() {
        Set<String> actual = Arrays.stream(ErrorType.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(actual)
                .as("상수를 추가했다면 이 목록에도 넣고, 그 김에 4xx·5xx 판단을 한 번 하라. "
                        + "그 판단을 강제하는 것이 이 테스트의 전부다")
                .isEqualTo(전체_상수);
    }

    @Test
    @DisplayName("서버 오류로 분류된 것은 이 목록뿐이다")
    void 서버_오류_목록이_고정돼_있다() {
        Set<ErrorType> actual = Arrays.stream(ErrorType.values())
                .filter(e -> e.getStatus().is5xxServerError())
                .collect(Collectors.toSet());

        assertThat(actual)
                .as("새 ErrorType 에 5xx 를 달면 그 오류가 ERROR + 스택트레이스로 승격된다. "
                        + "의도한 것이라면 이 목록을 함께 고치면 되고, 실수라면 여기서 걸린다")
                .isEqualTo(우리_쪽_문제);
    }

    @ParameterizedTest
    @EnumSource(ErrorType.class)
    @DisplayName("모든 ErrorType 에 status 가 채워져 있다")
    void 모든_ErrorType에_상태코드가_있다(ErrorType errorType) {
        assertThat(errorType.getStatus()).isNotNull();
        assertThat(errorType.getStatus().isError())
                .as("%s 의 상태 코드가 오류 범위가 아니다", errorType.name())
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(ErrorType.class)
    @DisplayName("코드가 비어 있지 않고 서로 겹치지 않는다")
    void 코드가_고유하다(ErrorType errorType) {
        assertThat(errorType.getCode()).isNotBlank();

        long same = Arrays.stream(ErrorType.values())
                .filter(e -> e.getCode().equals(errorType.getCode()))
                .count();
        assertThat(same)
                .as("%s 의 코드 %s 가 다른 상수와 겹친다 — 클라이언트가 둘을 구분할 수 없다",
                        errorType.name(), errorType.getCode())
                .isEqualTo(1);
    }

    /**
     * 원인이 섞인 채로 남겨 둔 것들 — <b>셋</b>이다.
     *
     * <p>초판은 둘이라고 적었는데 리뷰가 세 번째를 찾았다. {@code API_KEY_NOT_FOUND} 는
     * {@code GetApiKeyUseCase} 에서 {@code SETTINGS_NOT_FOUND}
     * 와 <b>완전히 같은 논리</b>로 데이터 정합성 문제를 나타낸다. 그런데도 4xx 인 이유는 없는 키·
     * 남의 키와 같은 코드를 써서 열거 오라클을 막아야 하기 때문이다 — 나눌 수 없는 코드다.
     *
     * <p>셋 다 서버 쪽 분기가 던지기 직전에 직접 {@code log.error} + 스택트레이스를 남긴다.
     * 그것이 4xx 로 두는 대가를 치르는 방식이다. {@code API_KEY_NOT_FOUND} 의 두 자리는
     * 아무것도 남기지 않고 있었고 (리뷰 지적) UG-298 에서 채웠다.
     *
     * <p>이 테스트는 <b>그 잠정 상태를 잊지 않기 위한 것</b>이다. 코드를 나눠 분리가 끝나면
     * {@link #전체_목록이_고정돼_있다()} 가 먼저 걸리고, 그때 이 목록도 함께 지우면 된다.
     */
    @Test
    @DisplayName("원인이 섞인 코드는 셋이고 전부 4xx 로 남아 있다")
    void 원인이_섞인_코드를_기억한다() {
        Set<ErrorType> 섞임 = Set.of(
                ErrorType.INVALID_FILE_PATH,
                ErrorType.FAILURE_COMPRESSION_FILE,
                ErrorType.API_KEY_NOT_FOUND);

        assertThat(섞임)
                .allSatisfy(errorType -> assertThat(errorType.getStatus().is4xxClientError())
                        .as("%s 를 5xx 로 올리면 클라이언트가 보낸 깨진 파일이나 오타 난 키까지 "
                                + "ERROR 가 된다. 올리려면 코드를 먼저 나눠야 한다", errorType.name())
                        .isTrue());
    }

    @Test
    @DisplayName("from 은 모르는 이름을 INTERNAL_SERVER_ERROR 로 떨어뜨린다")
    void from_은_모르는_이름을_안전하게_처리한다() {
        // 하위 서비스가 자기 ErrorType 이름을 보내오는 경로가 있어 우리가 모르는 값이 들어온다.
        // 예외를 던지면 원래 오류가 가려지므로 기본값으로 떨어뜨린다.
        assertThat(ErrorType.from("존재하지_않는_이름")).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR);
        assertThat(ErrorType.from(null)).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR);
        assertThat(ErrorType.from("API_KEY_NOT_FOUND")).isEqualTo(ErrorType.API_KEY_NOT_FOUND);
    }
}

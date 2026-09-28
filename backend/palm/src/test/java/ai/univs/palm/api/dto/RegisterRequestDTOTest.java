package ai.univs.palm.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 등록 요청이 호출자가 발급한 palmId 를 받는가 (UG-337).
 *
 * <p>palm 에는 컨트롤러 테스트 기반이 없어 DTO 를 직접 본다 — 형식 검증과 입력 변환 두 가지다.
 * 컨트롤러는 {@code @ModelAttribute @Valid} 로 이 DTO 를 받으므로, 여기서 검증이 걸리면 요청도
 * 400 으로 거절된다.
 */
@DisplayName("UG-337: 팜 등록 요청의 palmId")
class RegisterRequestDTOTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final String 발급 = "0f8fad5b-d9cb-469f-a165-70867728950e";

    private static RegisterRequestDTO 요청(String palmId) {
        return new RegisterRequestDTO("branch-A",
                new MockMultipartFile("palmImage", "p.jpg", "image/jpeg", new byte[]{1}),
                "txn", "client", false, palmId);
    }

    private static boolean 통과(String palmId) {
        return VALIDATOR.validateProperty(요청(palmId), "palmId").isEmpty();
    }

    @Test
    @DisplayName("UUID 는 받는다")
    void uuid_는_받는다() {
        assertThat(통과(발급)).isTrue();
        assertThat(통과(발급.toUpperCase())).as("대문자 16진수도 UUID 다").isTrue();
    }

    @Test
    @DisplayName("주지 않거나 빈 값이면 통과한다 — 서버가 발급한다")
    void 없으면_통과() {
        assertThat(통과(null)).isTrue();
        assertThat(통과("")).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"not-a-uuid", "0f8fad5bd9cb469fa16570867728950e", " 0f8fad5b-d9cb-469f-a165-70867728950e",
            "0f8fad5b-d9cb-469f-a165-70867728950e-extra"})
    @DisplayName("UUID 가 아니면 INVALID_PALM_ID_FORMAT 으로 거절한다")
    void uuid_가_아니면_거절(String palmId) {
        var violations = VALIDATOR.validateProperty(요청(palmId), "palmId");

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("INVALID_PALM_ID_FORMAT");
    }

    /**
     * 입력으로 그대로 옮긴다. 빠지면 유스케이스가 새 id 를 발급해 gate 가 남긴 id 가 가리키는 것이
     * 없어진다.
     */
    @Test
    @DisplayName("준 palmId 를 유스케이스 입력으로 옮긴다")
    void 입력으로_옮긴다() {
        assertThat(요청(발급).toV2RegisterInput().palmId()).isEqualTo(발급);
    }

    @Test
    @DisplayName("주지 않으면 빈 값으로 옮긴다 — 유스케이스가 발급한다")
    void 없으면_빈값() {
        assertThat(요청(null).toV2RegisterInput().palmId()).isEmpty();
    }
}

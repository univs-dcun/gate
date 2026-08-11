package ai.univs.gate.modules.feature.api.dto.face;

import ai.univs.gate.modules.feature.application.input.face.IdentifyCandidatesByDescriptorInput;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UG-314 요청 계약 — 반박 리뷰 후속.
 *
 * <p>리뷰가 이 DTO 에 뮤테이션 3개를 심었고 <b>전부 생존</b>했다. face 와 match 는 같은 커밋에서
 * 각각 8건·7건의 엔드포인트 검증 테스트를 받았는데 gate 만 0건이었다 — 하필 이 티켓의 두 헤드라인
 * 결정(백분율 스케일, 기본값 1)이 사는 곳이다.
 *
 * <ul>
 *   <li>{@code maxCandidates == null ? 1 : maxCandidates} → {@code maxCandidates} 로 바꿔도
 *       통과했다. 프로덕션에서는 <b>미전송 시 언박싱 NPE → 500</b> 이고, 그게 곧 문서가
 *       공표한 "기본값 1" 경로다.</li>
 *   <li>{@code @DecimalMax("100.0")} → {@code "10000.0"} 으로 바꿔도 통과했다.</li>
 *   <li>{@code @DecimalMin(inclusive = false)} → inclusive 로 바꿔도 통과했다.
 *       {@code threshold=0} 이 통과하면 갤러리 전원이 후보가 된다.</li>
 * </ul>
 *
 * <p>컨트롤러를 띄우지 않고 Bean Validation 을 직접 돌린다 — 검사 대상이 어노테이션 자체라
 * MockMvc 를 거칠 이유가 없다.
 */
@DisplayName("UG-314: 1:N 후보 목록 요청 계약 (gate)")
class IdentifyCandidatesRequestContractTest {

    /** 8바이트 헤더 + 512바이트 본문을 Basic base64 로 인코딩한 것 — 실제로 통과하는 값이다. */
    private static final String DESCRIPTOR =
            java.util.Base64.getEncoder().encodeToString(new byte[520]);
    private static final Long ACCOUNT_ID = 10L;
    private static final String API_KEY = "gate_test-api-key";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void 준비() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void 정리() {
        factory.close();
    }

    private static IdentifyCandidatesByDescriptorRequestDTO 요청(String threshold, Integer maxCandidates) {
        return new IdentifyCandidatesByDescriptorRequestDTO(
                DESCRIPTOR,
                threshold == null ? null : new BigDecimal(threshold),
                maxCandidates,
                null);
    }

    private static Set<String> 위반(IdentifyCandidatesByDescriptorRequestDTO request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("threshold — 백분율 0 초과 100 이하")
    class Threshold {

        @Test
        @DisplayName("누락이면 REQUIRED_THRESHOLD")
        void 누락() {
            assertThat(위반(요청(null, 5))).contains("REQUIRED_THRESHOLD");
        }

        @Test
        @DisplayName("0 은 거부한다 — 통과하면 갤러리 전원이 후보가 된다")
        void 영() {
            assertThat(위반(요청("0", 5)))
                    .as("@DecimalMin 이 inclusive 로 바뀌면 임계치의 의미가 사라진다")
                    .contains("INVALID_THRESHOLD");
            assertThat(위반(요청("0.00", 5))).contains("INVALID_THRESHOLD");
        }

        @Test
        @DisplayName("음수는 거부한다")
        void 음수() {
            assertThat(위반(요청("-1", 5))).contains("INVALID_THRESHOLD");
        }

        @ParameterizedTest(name = "threshold={0}")
        @ValueSource(strings = {"0.01", "70.02", "85.5", "99.99", "100", "100.00"})
        @DisplayName("0 초과 100 이하는 통과한다 (소수점 포함)")
        void 유효범위(String threshold) {
            assertThat(위반(요청(threshold, 5))).isEmpty();
        }

        @ParameterizedTest(name = "threshold={0}")
        @ValueSource(strings = {"100.01", "101", "1000"})
        @DisplayName("100 을 넘으면 거부한다")
        void 상한_초과(String threshold) {
            // 상한이 느슨해지면 도메인 스케일로 나눈 값이 1.0 을 넘어 face 에서 400 이 된다.
            // 여기서 막지 않으면 실패 지점이 한 서비스 뒤로 밀린다.
            assertThat(위반(요청(threshold, 5))).contains("INVALID_THRESHOLD");
        }
    }

    @Nested
    @DisplayName("maxCandidates — 1 ~ 100, 기본값 1")
    class MaxCandidates {

        @Test
        @DisplayName("미전송이면 1 이 적용된다 — 500 이 나면 안 된다")
        void 기본값() {
            IdentifyCandidatesByDescriptorRequestDTO request = 요청("85.00", null);

            assertThat(위반(request))
                    .as("OPTIONAL 이므로 검증 자체는 통과해야 한다")
                    .isEmpty();

            IdentifyCandidatesByDescriptorInput input =
                    request.toIdentifyCandidatesByDescriptorInput(ACCOUNT_ID, API_KEY);

            assertThat(input.maxCandidates())
                    .as("널 병합이 빠지면 여기서 언박싱 NPE 가 나고 클라이언트는 500 을 받는다 "
                            + "— 문서가 공표한 '기본값 1' 경로가 곧 장애 경로가 된다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("보낸 값은 그대로 전달된다")
        void 전달() {
            var input = 요청("85.00", 37).toIdentifyCandidatesByDescriptorInput(ACCOUNT_ID, API_KEY);

            assertThat(input.maxCandidates()).isEqualTo(37);
        }

        @ParameterizedTest(name = "maxCandidates={0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("1 미만은 거부한다")
        void 하한_미만(int maxCandidates) {
            assertThat(위반(요청("85.00", maxCandidates))).contains("INVALID_MAX_CANDIDATES");
        }

        @Test
        @DisplayName("100 은 통과하고 101 은 거부한다")
        void 상한() {
            assertThat(위반(요청("85.00", 100))).isEmpty();
            assertThat(위반(요청("85.00", 101)))
                    .as("상한이 없으면 갤러리 전체를 한 번에 끌어올 수 있다 (palm pageSize 사고와 같은 형태)")
                    .contains("INVALID_MAX_CANDIDATES");
        }
    }

    @Nested
    @DisplayName("스케일·전달값")
    class 전달값 {

        @Test
        @DisplayName("threshold 는 백분율 그대로 입력에 실린다 — 나누는 것은 유스케이스다")
        void 백분율_보존() {
            var input = 요청("85.33", 5).toIdentifyCandidatesByDescriptorInput(ACCOUNT_ID, API_KEY);

            assertThat(input.thresholdPercent()).isEqualByComparingTo("85.33");
        }

        @Test
        @DisplayName("transactionUuid 를 안 보내면 생성해서 채운다")
        void 트랜잭션_생성() {
            var input = 요청("85.00", 5).toIdentifyCandidatesByDescriptorInput(ACCOUNT_ID, API_KEY);

            assertThat(input.transactionUuid()).isNotBlank();
            assertThat(input.accountId()).isEqualTo(ACCOUNT_ID);
            assertThat(input.apiKey()).isEqualTo(API_KEY);
            assertThat(input.descriptor()).isEqualTo(DESCRIPTOR);
        }

        @Test
        @DisplayName("descriptor 는 고정 길이를 검증한다")
        void 디스크립터_길이() {
            var 짧은것 = new IdentifyCandidatesByDescriptorRequestDTO(
                    "too-short", new BigDecimal("85.00"), 5, null);

            assertThat(위반(짧은것)).contains("INVALID_DESCRIPTOR");
        }
    }
}

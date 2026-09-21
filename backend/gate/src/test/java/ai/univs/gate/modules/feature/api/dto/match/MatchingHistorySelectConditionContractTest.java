package ai.univs.gate.modules.feature.api.dto.match;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** UG-326: 목록 조회 요청 계약 — DELETE 필터 수용, includeDeletions 기본값. */
@DisplayName("UG-326: 이력 목록 요청 계약")
class MatchingHistorySelectConditionContractTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll static void 준비() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }
    @AfterAll  static void 정리() { factory.close(); }

    private static MatchingHistorySelectCondition 요청(String matchType, Boolean includeDeletions) {
        return new MatchingHistorySelectCondition(null, matchType, null, null, null, null, null, null, includeDeletions, null, null);
    }

    private static Set<String> 위반(MatchingHistorySelectCondition c) {
        return validator.validate(c).stream().map(ConstraintViolation::getMessage).collect(java.util.stream.Collectors.toSet());
    }

    @ParameterizedTest(name = "matchType={0}")
    @ValueSource(strings = {"REGISTER", "DELETE", "VERIFY", "VERIFY_ID", "VERIFY_IMAGE", "VERIFY_DESCRIPTOR", "IDENTIFY", "LIVENESS", "ALL"})
    @DisplayName("특징점 관리(REGISTER·DELETE)와 인증 타입이 모두 통과한다")
    void 허용_타입(String type) {
        assertThat(위반(요청(type, null))).isEmpty();
    }

    @ParameterizedTest(name = "matchType={0}")
    @ValueSource(strings = {"REMOVE", "delete", "UPDATE", "MATCH"})
    @DisplayName("그 밖의 값은 INVALID_MATCH_TYPE_CONDITION")
    void 거부_타입(String type) {
        assertThat(위반(요청(type, null))).contains("INVALID_MATCH_TYPE_CONDITION");
    }

    @Test
    @DisplayName("includeDeletions 미전송이면 false — 옛 클라이언트는 삭제 행을 받지 않는다")
    void includeDeletions_기본값() {
        assertThat(요청(null, null).toMatchingHistoryQuery(1L, "k").includeDeletions()).isFalse();
        assertThat(요청(null, false).toMatchingHistoryQuery(1L, "k").includeDeletions()).isFalse();
        assertThat(요청(null, true).toMatchingHistoryQuery(1L, "k").includeDeletions()).isTrue();
        assertThat(요청(null, null).toMatchingHistoryQuery(1L, "k").matchType()).isEqualTo("ALL");
    }
}

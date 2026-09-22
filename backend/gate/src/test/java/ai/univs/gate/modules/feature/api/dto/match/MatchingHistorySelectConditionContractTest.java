package ai.univs.gate.modules.feature.api.dto.match;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import ai.univs.gate.modules.feature.application.result.match.MatchHistoryResult;
import ai.univs.gate.modules.feature.domain.enums.ActivitySource;
import ai.univs.gate.modules.feature.domain.enums.ActivityType;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.infrastructure.persistence.query.MatchHistoryQuery;
import ai.univs.gate.shared.auth.UserContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
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

    @AfterEach void 컨텍스트_정리() { UserContext.clear(); }

    private static MatchingHistorySelectCondition 전체(String keyword, String matchType, String featureType, String result,
                                                     Integer page, Integer size, String start, String end, Boolean incl, String dir, String sort) {
        return new MatchingHistorySelectCondition(keyword, matchType, featureType, result, page, size, start, end, incl, dir, sort);
    }

    @Test
    @DisplayName("미전송 필드의 기본값 — featureType ALL, 결과 SUCCESS, 1페이지 10건, DESC/identifyTime, 날짜 없음")
    void 기본값() {
        MatchHistoryQuery q = 전체(null, null, null, null, null, null, null, null, null, null, null).toMatchingHistoryQuery(1L, "k");
        assertThat(q.matchType()).isEqualTo("ALL");
        assertThat(q.featureType()).isEqualTo("ALL");
        assertThat(q.matchResultType()).as("목록 기본은 성공만 — 예전 계약 그대로").isEqualTo("SUCCESS");
        assertThat(q.page()).isEqualTo(1);
        assertThat(q.pageSize()).isEqualTo(10);
        assertThat(q.hasDate()).isFalse();
        assertThat(q.startDateTime()).isNull();
        assertThat(q.endDateTime()).isNull();
        assertThat(q.direction()).isEqualTo("DESC");
        assertThat(q.sortBy()).isEqualTo("identifyTime");
        assertThat(q.includeDeletions()).isFalse();
    }

    @Test
    @DisplayName("보낸 값은 그대로 전달된다")
    void 명시값() {
        MatchHistoryQuery q = 전체("홍길", "DELETE", "FACE", "FAILURE", 3, 50, null, null, true, "ASC", "createdAt").toMatchingHistoryQuery(7L, "key");
        assertThat(q.accountId()).isEqualTo(7L);
        assertThat(q.apiKey()).isEqualTo("key");
        assertThat(q.matchingHistoryKeyword()).isEqualTo("홍길");
        assertThat(q.matchType()).isEqualTo("DELETE");
        assertThat(q.featureType()).isEqualTo("FACE");
        assertThat(q.matchResultType()).isEqualTo("FAILURE");
        assertThat(q.page()).isEqualTo(3);
        assertThat(q.pageSize()).isEqualTo(50);
        assertThat(q.direction()).isEqualTo("ASC");
        assertThat(q.sortBy()).isEqualTo("createdAt");
        assertThat(q.includeDeletions()).isTrue();
    }

    @Test
    @DisplayName("날짜는 둘 다 있어야 적용된다 — 하나만 보내면 hasDate=false 지만 값은 변환된다")
    void 날짜() {
        UserContext.set(UserContext.builder().timezone("Asia/Seoul").build());
        MatchHistoryQuery 하나 = 전체(null, null, null, null, null, null, "2026-09-01", null, null, null, null).toMatchingHistoryQuery(1L, "k");
        assertThat(하나.hasDate()).isFalse();
        assertThat(하나.startDateTime()).isNotNull();
        assertThat(하나.endDateTime()).isNull();

        MatchHistoryQuery 둘 = 전체(null, null, null, null, null, null, "2026-09-01", "2026-09-02", null, null, null).toMatchingHistoryQuery(1L, "k");
        assertThat(둘.hasDate()).isTrue();
        assertThat(둘.startDateTime()).isBefore(둘.endDateTime());
    }

    @Test
    @DisplayName("응답 DTO 는 source 와 이력 타입을 그대로 옮기고 실패 사유를 붙인다")
    void 응답_DTO_매핑() {
        MatchHistoryResult r = new MatchHistoryResult(3L, 1003L, 1L, FeatureType.FACE, ActivityType.DELETE, ActivitySource.FEATURE,
                LocalDateTime.of(2026, 9, 21, 1, 0), false, false, "fid", 7L, "홍길동", (BigDecimal) null, "img", null,
                "INTERNAL_SERVER_ERROR", "tx-1", true, LocalDateTime.of(2026, 9, 21, 1, 0), "ext-9");
        MatchingHistoryResponseDTO dto = MatchingHistoryResponseDTO.from(r, "서버 오류", "Asia/Seoul");
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(ActivitySource.FEATURE);
        assertThat(dto.matchType()).isEqualTo(ActivityType.DELETE);
        assertThat(dto.matchingHistoryId()).isEqualTo(3L);
        assertThat(dto.sequence()).isEqualTo(1003L);
        assertThat(dto.failureReason()).isEqualTo("서버 오류");
        assertThat(dto.similarity()).isNull();
        assertThat(dto.matchingTime()).isNotNull();
        assertThat(dto.externalKey()).as("UG-333").isEqualTo("ext-9");
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

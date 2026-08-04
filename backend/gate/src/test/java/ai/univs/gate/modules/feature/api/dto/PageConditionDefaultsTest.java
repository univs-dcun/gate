package ai.univs.gate.modules.feature.api.dto;

import ai.univs.gate.facade.feature.api.dto.FeatureSelectCondition;
import ai.univs.gate.modules.feature.api.dto.palm.PalmFeatureSelectCondition;
import ai.univs.gate.shared.utils.CustomPageable;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * UG-268 회귀 방지.
 *
 * <p>목록 조회 조건 DTO는 문서(docs/api/gate-api-docs.html)에 page/pageSize를 OPTIONAL로
 * 공표한다. 따라서 (1) 생략 시 기본값이 채워져야 하고 (2) 그 값으로 Pageable을 만들 수 있어야
 * 하며 (3) 범위 밖 값은 400으로 걸러져야 한다. 팜 조건 DTO가 primitive int라 (1)(2)를 어겨
 * 파라미터 없는 호출이 500으로 떨어졌다.
 */
class PageConditionDefaultsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> messagesOf(Object condition) {
        return validator.validate(condition).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Nested
    @DisplayName("GET /api/v1/feature/palm")
    class Palm {

        private PalmFeatureSelectCondition condition(Integer page, Integer pageSize) {
            return new PalmFeatureSelectCondition(page, pageSize, null, null, null, null);
        }

        @Test
        @DisplayName("파라미터를 생략하면 문서가 공표한 기본값 1/20이 채워진다")
        void 생략시_기본값() {
            var query = condition(null, null).toQuery(1L, "api-key");

            assertThat(query.page()).isEqualTo(1);
            assertThat(query.pageSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("생략된 기본값으로 Pageable을 만들 수 있다 — 이게 깨져서 500이 났다")
        void 생략시_Pageable_생성가능() {
            var query = condition(null, null).toQuery(1L, "api-key");

            assertThatCode(() -> CustomPageable.of(query.page(), query.pageSize()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("범위 밖 값은 INVALID_PAGE_COUNT로 걸러진다")
        void 범위밖_거절() {
            assertThat(messagesOf(condition(0, 20))).containsExactly("INVALID_PAGE_COUNT");
            assertThat(messagesOf(condition(1, 0))).containsExactly("INVALID_PAGE_COUNT");
            assertThat(messagesOf(condition(-1, 20))).containsExactly("INVALID_PAGE_COUNT");
            assertThat(messagesOf(condition(1, 1001))).containsExactly("INVALID_PAGE_COUNT");
        }

        @Test
        @DisplayName("경계값 1과 1000은 통과한다")
        void 경계값_통과() {
            assertThat(messagesOf(condition(1, 1))).isEmpty();
            assertThat(messagesOf(condition(1000, 1000))).isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /api/v1/feature (Face+Palm 통합)")
    class Feature {

        private FeatureSelectCondition condition(Integer page, Integer pageSize) {
            return new FeatureSelectCondition(null, null, page, pageSize, null, null, null);
        }

        @Test
        @DisplayName("파라미터를 생략하면 문서가 공표한 기본값 1/10이 채워진다")
        void 생략시_기본값() {
            var query = condition(null, null).toQuery(1L, "api-key", "Asia/Seoul");

            assertThat(query.page()).isEqualTo(1);
            assertThat(query.pageSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("범위 밖 값은 INVALID_PAGE_COUNT로 걸러진다")
        void 범위밖_거절() {
            // page=0이면 GetFeatureListUseCase의 offset이 음수가 된다
            assertThat(messagesOf(condition(0, 10))).containsExactly("INVALID_PAGE_COUNT");
            // 상한이 없으면 featureType=ALL 경로가 대량 행을 메모리로 병합한다
            assertThat(messagesOf(condition(1, 1001))).containsExactly("INVALID_PAGE_COUNT");
        }

        @Test
        @DisplayName("경계값 1과 1000은 통과한다")
        void 경계값_통과() {
            assertThat(messagesOf(condition(1, 1))).isEmpty();
            assertThat(messagesOf(condition(1000, 1000))).isEmpty();
        }
    }
}

package ai.univs.face.application.service;

import ai.univs.face.shared.exception.InvalidFaceImageException;
import ai.univs.face.shared.web.enums.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimilarityParserTest {

    private SimilarityParser similarityParser;

    private static final double DEFAULT_THRESHOLD = 0.85;

    @BeforeEach
    void setUp() {
        similarityParser = new SimilarityParser();
        ReflectionTestUtils.setField(similarityParser, "FACE_MATCH_THRESHOLD", DEFAULT_THRESHOLD);
    }

    @Nested
    @DisplayName("parseDoubleSimilarity")
    class ParseDoubleSimilarity {

        @Test
        @DisplayName("유효한 숫자 문자열을 Double로 변환한다")
        void parse_validString_returnsDouble() {
            Double result = similarityParser.parseDoubleSimilarity("0.92");

            assertThat(result).isEqualTo(0.92);
        }

        @Test
        @DisplayName("정수 형태의 문자열도 Double로 변환한다")
        void parse_integerString_returnsDouble() {
            Double result = similarityParser.parseDoubleSimilarity("1");

            assertThat(result).isEqualTo(1.0);
        }

        @Test
        @DisplayName("숫자가 아닌 문자열은 InvalidFaceImageException을 던진다")
        void parse_nonNumericString_throwsException() {
            assertThatThrownBy(() -> similarityParser.parseDoubleSimilarity("invalid"))
                    .isInstanceOf(InvalidFaceImageException.class)
                    .extracting(e -> ((InvalidFaceImageException) e).getErrorType())
                    .isEqualTo(ErrorType.NO_DOUBLE_SIMILARITY);
        }

        @Test
        @DisplayName("빈 문자열은 InvalidFaceImageException을 던진다")
        void parse_emptyString_throwsException() {
            assertThatThrownBy(() -> similarityParser.parseDoubleSimilarity(""))
                    .isInstanceOf(InvalidFaceImageException.class);
        }

        @Test
        @DisplayName("null은 NullPointerException을 던진다 (catch 대상 아님)")
        void parse_null_throwsNullPointerException() {
            assertThatThrownBy(() -> similarityParser.parseDoubleSimilarity(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("isMatchingBySimilarity")
    class IsMatchingBySimilarity {

        @Test
        @DisplayName("임계값보다 높으면 매칭으로 판단한다")
        void isMatching_aboveThreshold_returnsTrue() {
            assertThat(similarityParser.isMatchingBySimilarity(0.90)).isTrue();
        }

        @Test
        @DisplayName("임계값과 동일하면 매칭으로 판단한다")
        void isMatching_equalToThreshold_returnsTrue() {
            assertThat(similarityParser.isMatchingBySimilarity(DEFAULT_THRESHOLD)).isTrue();
        }

        @Test
        @DisplayName("임계값보다 낮으면 미매칭으로 판단한다")
        void isMatching_belowThreshold_returnsFalse() {
            assertThat(similarityParser.isMatchingBySimilarity(0.84)).isFalse();
        }

        @Test
        @DisplayName("0.0은 미매칭으로 판단한다")
        void isMatching_zero_returnsFalse() {
            assertThat(similarityParser.isMatchingBySimilarity(0.0)).isFalse();
        }
    }

    @Nested
    @DisplayName("getThreshold / getThresholdString")
    class GetThreshold {

        @Test
        @DisplayName("설정된 임계값을 double로 반환한다")
        void getThreshold_returnsConfiguredValue() {
            assertThat(similarityParser.getThreshold()).isEqualTo(DEFAULT_THRESHOLD);
        }

        @Test
        @DisplayName("설정된 임계값을 String으로 반환한다")
        void getThresholdString_returnsStringValue() {
            assertThat(similarityParser.getThresholdString()).isEqualTo("0.85");
        }

        @Test
        @DisplayName("커스텀 임계값이 올바르게 반영된다")
        void getThreshold_customValue_returnsCustomThreshold() {
            ReflectionTestUtils.setField(similarityParser, "FACE_MATCH_THRESHOLD", 0.70);

            assertThat(similarityParser.getThreshold()).isEqualTo(0.70);
            assertThat(similarityParser.getThresholdString()).isEqualTo("0.7");
        }
    }
}

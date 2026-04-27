package ai.univs.match.shared.utils;

import ai.univs.match.application.enums.DescriptorSpec;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SimilarityCalculator")
class SimilarityCalculatorTest {

    @Nested
    @DisplayName("getSimilarityByDistance")
    class GetSimilarityByDistance {

        @ParameterizedTest(name = "distance={0}일 때 유사도는 1.00000")
        @ValueSource(doubles = {0.0, -0.001, -1.0, -100.0})
        @DisplayName("거리가 0 이하이면 유사도 1.00000을 반환한다")
        void whenDistanceIsZeroOrNegative_thenReturnMaxSimilarity(double distance) {
            String result = SimilarityCalculator.getSimilarityByDistance(distance, DescriptorSpec.VERSION_59);

            assertThat(result).isEqualTo("1.00000");
        }

        @Test
        @DisplayName("거리가 양수이면 Platt scaling 공식으로 유사도를 계산한다 (VERSION_59)")
        void whenDistanceIsPositive_thenApplyPlattScaling_version59() {
            double distance = 1.0;
            DescriptorSpec spec = DescriptorSpec.VERSION_59;

            String result = SimilarityCalculator.getSimilarityByDistance(distance, spec);

            double similarity = Double.parseDouble(result);
            // 1 / (1 + exp(A * 1.0 + B)) 로 계산한 예상 범위 검증 (VERSION_59: A≈15.83, B≈-18.53)
            // exp(-2.70) ≈ 0.0671 → similarity ≈ 0.937
            assertThat(similarity).isCloseTo(0.937, within(0.002));
        }

        @Test
        @DisplayName("거리가 양수이면 Platt scaling 공식으로 유사도를 계산한다 (VERSION_60)")
        void whenDistanceIsPositive_thenApplyPlattScaling_version60() {
            double distance = 1.0;
            DescriptorSpec spec = DescriptorSpec.VERSION_60;

            String result = SimilarityCalculator.getSimilarityByDistance(distance, spec);

            double similarity = Double.parseDouble(result);
            // VERSION_60: A≈15.88, B≈-18.83 → exp(-2.95) ≈ 0.0523 → similarity ≈ 0.950
            assertThat(similarity).isCloseTo(0.950, within(0.002));
        }

        @Test
        @DisplayName("거리가 양수이면 Platt scaling 공식으로 유사도를 계산한다 (VERSION_62)")
        void whenDistanceIsPositive_thenApplyPlattScaling_version62() {
            double distance = 1.0;
            DescriptorSpec spec = DescriptorSpec.VERSION_62;

            String result = SimilarityCalculator.getSimilarityByDistance(distance, spec);

            double similarity = Double.parseDouble(result);
            // VERSION_62: A≈15.57, B≈-18.32 → exp(-2.75) ≈ 0.0639 → similarity ≈ 0.940
            assertThat(similarity).isCloseTo(0.940, within(0.002));
        }

        @Test
        @DisplayName("각 DescriptorSpec은 서로 다른 유사도를 계산한다 (거리 동일)")
        void whenSameDistanceWithDifferentSpec_thenReturnDifferentSimilarity() {
            double distance = 1.0;

            double sim59 = Double.parseDouble(SimilarityCalculator.getSimilarityByDistance(distance, DescriptorSpec.VERSION_59));
            double sim60 = Double.parseDouble(SimilarityCalculator.getSimilarityByDistance(distance, DescriptorSpec.VERSION_60));
            double sim62 = Double.parseDouble(SimilarityCalculator.getSimilarityByDistance(distance, DescriptorSpec.VERSION_62));

            assertThat(sim59).isNotEqualTo(sim60);
            assertThat(sim59).isNotEqualTo(sim62);
            assertThat(sim60).isNotEqualTo(sim62);
        }

        @Test
        @DisplayName("거리가 클수록 유사도는 낮아진다 (단조 감소)")
        void whenDistanceIncreases_thenSimilarityDecreases() {
            DescriptorSpec spec = DescriptorSpec.VERSION_59;

            double sim1 = Double.parseDouble(SimilarityCalculator.getSimilarityByDistance(0.5, spec));
            double sim2 = Double.parseDouble(SimilarityCalculator.getSimilarityByDistance(1.0, spec));
            double sim3 = Double.parseDouble(SimilarityCalculator.getSimilarityByDistance(2.0, spec));

            assertThat(sim1).isGreaterThan(sim2);
            assertThat(sim2).isGreaterThan(sim3);
        }

        @Test
        @DisplayName("유사도는 항상 0과 1 사이의 값이다")
        void whenAnyPositiveDistance_thenSimilarityIsBetweenZeroAndOne() {
            DescriptorSpec spec = DescriptorSpec.VERSION_59;
            double[] distances = {0.001, 0.5, 1.0, 2.0, 5.0, 100.0};

            for (double distance : distances) {
                double similarity = Double.parseDouble(SimilarityCalculator.getSimilarityByDistance(distance, spec));
                assertThat(similarity)
                        .as("distance=%s 일 때 유사도는 [0, 1] 범위여야 한다", distance)
                        .isBetween(0.0, 1.0);
            }
        }

        @Test
        @DisplayName("반환값은 소수점 5자리 형식의 문자열이다")
        void whenCalled_thenReturnStringWithFiveDecimalPlaces() {
            String result = SimilarityCalculator.getSimilarityByDistance(1.0, DescriptorSpec.VERSION_59);

            assertThat(result).matches("\\d+\\.\\d{5}");
        }
    }

    @Nested
    @DisplayName("getSimilarityTypeByVersionOfDescriptor")
    class GetSimilarityTypeByVersionOfDescriptor {

        private byte[] descriptorWithVersionAt(int version) {
            byte[] descriptor = new byte[520]; // 8 bytes header + 512 bytes body
            descriptor[4] = (byte) version;
            return descriptor;
        }

        @Test
        @DisplayName("descriptor[4]가 59이면 VERSION_59를 반환한다")
        void whenVersionIs59_thenReturnVersion59() {
            byte[] descriptor = descriptorWithVersionAt(59);

            DescriptorSpec result = SimilarityCalculator.getSimilarityTypeByVersionOfDescriptor(descriptor);

            assertThat(result).isEqualTo(DescriptorSpec.VERSION_59);
        }

        @Test
        @DisplayName("descriptor[4]가 60이면 VERSION_60을 반환한다")
        void whenVersionIs60_thenReturnVersion60() {
            byte[] descriptor = descriptorWithVersionAt(60);

            DescriptorSpec result = SimilarityCalculator.getSimilarityTypeByVersionOfDescriptor(descriptor);

            assertThat(result).isEqualTo(DescriptorSpec.VERSION_60);
        }

        @Test
        @DisplayName("descriptor[4]가 62이면 VERSION_62를 반환한다")
        void whenVersionIs62_thenReturnVersion62() {
            byte[] descriptor = descriptorWithVersionAt(62);

            DescriptorSpec result = SimilarityCalculator.getSimilarityTypeByVersionOfDescriptor(descriptor);

            assertThat(result).isEqualTo(DescriptorSpec.VERSION_62);
        }

        @ParameterizedTest(name = "version={0}")
        @ValueSource(ints = {0, 1, 58, 61, 63, 100})
        @DisplayName("지원하지 않는 버전이면 CustomFaceMatcherException을 던진다")
        void whenVersionIsUnsupported_thenThrowException(int version) {
            byte[] descriptor = descriptorWithVersionAt(version);

            assertThatThrownBy(() -> SimilarityCalculator.getSimilarityTypeByVersionOfDescriptor(descriptor))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .satisfies(ex -> {
                        CustomFaceMatcherException faceEx = (CustomFaceMatcherException) ex;
                        assertThat(faceEx.getErrorType()).isEqualTo(ErrorType.NOT_SUPPORTED_VERSION);
                    });
        }

        @Test
        @DisplayName("버전 정보는 descriptor 배열의 4번 인덱스(5번째 바이트)에서 읽는다")
        void whenVersionAtIndex4_thenReadFromCorrectPosition() {
            byte[] descriptor = new byte[520];
            descriptor[0] = 59; // 0번 인덱스에 잘못된 위치에 59 설정
            descriptor[4] = 60; // 올바른 위치

            DescriptorSpec result = SimilarityCalculator.getSimilarityTypeByVersionOfDescriptor(descriptor);

            assertThat(result).isEqualTo(DescriptorSpec.VERSION_60);
        }
    }

    @Nested
    @DisplayName("parseDoubleSimilarity")
    class ParseDoubleSimilarity {

        @Test
        @DisplayName("유효한 숫자 문자열을 Double로 변환한다")
        void whenValidNumberString_thenReturnDouble() {
            assertThat(SimilarityCalculator.parseDoubleSimilarity("0.85000")).isEqualTo(0.85);
            assertThat(SimilarityCalculator.parseDoubleSimilarity("1.00000")).isEqualTo(1.0);
            assertThat(SimilarityCalculator.parseDoubleSimilarity("0.00000")).isEqualTo(0.0);
            assertThat(SimilarityCalculator.parseDoubleSimilarity("0.50000")).isEqualTo(0.5);
        }

        @Test
        @DisplayName("정수 형태의 문자열도 Double로 변환한다")
        void whenIntegerString_thenReturnDouble() {
            assertThat(SimilarityCalculator.parseDoubleSimilarity("1")).isEqualTo(1.0);
            assertThat(SimilarityCalculator.parseDoubleSimilarity("0")).isEqualTo(0.0);
        }

        @ParameterizedTest(name = "입력=\"{0}\"")
        @ValueSource(strings = {"abc", "0.8abc", "1,0", "", " ", "null"})
        @DisplayName("숫자로 변환할 수 없는 문자열이면 INTERNAL_SERVER_ERROR로 예외를 던진다")
        void whenInvalidString_thenThrowExceptionWithInternalServerError(String invalid) {
            assertThatThrownBy(() -> SimilarityCalculator.parseDoubleSimilarity(invalid))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .satisfies(ex -> {
                        CustomFaceMatcherException faceEx = (CustomFaceMatcherException) ex;
                        assertThat(faceEx.getErrorType()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR);
                    });
        }
    }

    @Nested
    @DisplayName("convertDoubleSimilarityToString")
    class ConvertDoubleSimilarityToString {

        @Test
        @DisplayName("소수점 5자리 문자열로 변환한다")
        void whenConverting_thenReturnFiveDecimalPlaces() {
            assertThat(SimilarityCalculator.convertDoubleSimilarityToString(0.5)).isEqualTo("0.50000");
            assertThat(SimilarityCalculator.convertDoubleSimilarityToString(1.0)).isEqualTo("1.00000");
            assertThat(SimilarityCalculator.convertDoubleSimilarityToString(0.0)).isEqualTo("0.00000");
        }

        @Test
        @DisplayName("소수점 6번째 자리가 5 이상이면 올림(HALF_UP)한다")
        void whenSixthDecimalIsRoundUp_thenRoundUp() {
            // 0.123456 → 0.12346 (6번째 자리 6 ≥ 5 이므로 올림)
            assertThat(SimilarityCalculator.convertDoubleSimilarityToString(0.123456)).isEqualTo("0.12346");
            // 0.123465 → 0.12347 (6번째 자리 6 ≥ 5 이므로 올림)
            assertThat(SimilarityCalculator.convertDoubleSimilarityToString(0.123465)).isEqualTo("0.12347");
        }

        @Test
        @DisplayName("소수점 6번째 자리가 5 미만이면 버림(HALF_UP)한다")
        void whenSixthDecimalIsTruncated_thenTruncate() {
            // 0.123454... → 0.12345
            assertThat(SimilarityCalculator.convertDoubleSimilarityToString(0.123454)).isEqualTo("0.12345");
            assertThat(SimilarityCalculator.convertDoubleSimilarityToString(0.999994)).isEqualTo("0.99999");
        }

        @Test
        @DisplayName("이미 5자리인 경우 그대로 유지한다")
        void whenExactlyFiveDecimalPlaces_thenNoChange() {
            assertThat(SimilarityCalculator.convertDoubleSimilarityToString(0.85000)).isEqualTo("0.85000");
            assertThat(SimilarityCalculator.convertDoubleSimilarityToString(0.12345)).isEqualTo("0.12345");
        }

        @Test
        @DisplayName("반환값은 소수점 5자리 형식의 문자열이다")
        void whenCalled_thenReturnStringMatchingFormat() {
            double[] values = {0.0, 0.25, 0.5, 0.75, 1.0, 0.85000, 0.99999};
            for (double value : values) {
                String result = SimilarityCalculator.convertDoubleSimilarityToString(value);
                assertThat(result)
                        .as("입력값 %s 의 결과는 소수점 5자리 형식이어야 한다", value)
                        .matches("\\d+\\.\\d{5}");
            }
        }
    }

    @Nested
    @DisplayName("getSimilarityByDistance + parseDoubleSimilarity 왕복 변환")
    class RoundTripConsistency {

        @Test
        @DisplayName("getSimilarityByDistance 결과를 parseDoubleSimilarity로 역변환하면 유효한 double을 반환한다")
        void whenRoundTrip_thenReturnValidDouble() {
            double distance = 1.0;
            DescriptorSpec spec = DescriptorSpec.VERSION_59;

            String similarityStr = SimilarityCalculator.getSimilarityByDistance(distance, spec);
            Double similarity = SimilarityCalculator.parseDoubleSimilarity(similarityStr);

            assertThat(similarity).isNotNull();
            assertThat(similarity).isBetween(0.0, 1.0);
        }
    }
}

package ai.univs.match.shared.utils;

import ai.univs.match.application.enums.DescriptorSpec;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static ai.univs.match.shared.web.enums.ErrorType.INTERNAL_SERVER_ERROR;

@Slf4j
public class SimilarityCalculator {

    // 거리로 유사도 계산
    public static String getSimilarityByDistance(Double distance, DescriptorSpec descriptorSpec) {
        if (distance <= 0)
            return convertDoubleSimilarityToString(1.0);

        double exp = Math.exp(descriptorSpec.getDescriptorPlattSacleingA() * distance + descriptorSpec.getDescriptorPlattSacleingB());
        return convertDoubleSimilarityToString((1.0f / (1.0f + exp)));
    }

    // 특징점 버전을 기반으로 유사도 계산을 위한 타입 조회
    public static DescriptorSpec getSimilarityTypeByVersionOfDescriptor(byte[] descriptor) {
        int version = descriptor[4];
        return DescriptorSpec.fromVersion(version);
    }

    // 유사도 점수 문자열을 소수점으로 변환
    public static Double parseDoubleSimilarity(String similarity) {
        try {
            return Double.parseDouble(similarity);
        } catch (NumberFormatException e) {
            log.error("Failed to parse the similarity string as a double type. similarity: {}", similarity);
            throw new CustomFaceMatcherException(INTERNAL_SERVER_ERROR);
        }
    }

    // 유사도 점수 소수점을 문자열로 변환
    public static String convertDoubleSimilarityToString(double similarity) {
        return new BigDecimal(similarity).setScale(5, RoundingMode.HALF_UP).toString();
    }
}

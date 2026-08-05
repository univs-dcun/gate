package ai.univs.gate.modules.feature.application.result.face;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;

import java.time.LocalDateTime;

/**
 * descriptor 기반 등록 결과 (UG-279).
 *
 * <p>{@link FaceFeatureResult} 와 분리한 이유는 descriptor 경로에 존재할 수 없는 필드를 응답에서
 * 빼기 위해서다 — {@code featureImagePath}(이미지 파일이 없다), {@code description}(받지 않는다),
 * {@code checkLiveness}(무조건 OFF 라 알릴 값이 없다).
 */
public record FaceFeatureByDescriptorResult(
        Long faceFeatureId,
        String featureId,
        LocalDateTime createdAt,
        String transactionUuid
) {

    public static FaceFeatureByDescriptorResult from(BiometricFeature feature) {
        return new FaceFeatureByDescriptorResult(
                feature.getId(),
                feature.getFeatureId(),
                feature.getCreatedAt(),
                feature.getTransactionUuid());
    }
}

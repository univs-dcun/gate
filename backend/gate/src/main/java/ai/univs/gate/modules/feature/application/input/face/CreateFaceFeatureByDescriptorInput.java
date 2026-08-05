package ai.univs.gate.modules.feature.application.input.face;

/**
 * descriptor 기반 특징점 얼굴 등록 입력 (UG-279).
 *
 * <p>{@code CreateFeatureInput} 과 달리 {@code featureImage} 와 {@code description} 이 없다.
 * description 은 신규 API 에서 아예 받지 않기로 결정했다.
 */
public record CreateFaceFeatureByDescriptorInput(
        Long accountId,
        String apiKey,
        String descriptor,
        String transactionUuid
) {
}

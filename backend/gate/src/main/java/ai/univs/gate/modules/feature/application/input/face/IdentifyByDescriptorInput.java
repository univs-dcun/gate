package ai.univs.gate.modules.feature.application.input.face;

/**
 * descriptor 기반 1:N 매칭 입력 (UG-279).
 *
 * <p>{@link IdentifyInput} 과 달리 {@code matchingFeatureImage} 와 {@code callerType} 이 없다.
 * callerType 이 없는 이유는 신규 API 가 웹훅/데모 알림을 발행하지 않기 때문이다 —
 * {@code IdentifyByDescriptorUseCase} 의 주석 참고.
 */
public record IdentifyByDescriptorInput(
        Long accountId,
        String apiKey,
        String descriptor,
        String transactionUuid
) {
}

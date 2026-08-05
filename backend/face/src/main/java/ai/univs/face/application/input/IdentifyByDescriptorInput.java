package ai.univs.face.application.input;

/**
 * descriptor 기반 1:N 매칭 입력 (UG-279).
 *
 * <p>{@link IdentifyInput} 과 달리 {@code faceImage}, {@code checkLiveness},
 * {@code checkMultiFace} 가 없다. 사유는 {@link RegisterByDescriptorInput} 과 같다.
 */
public record IdentifyByDescriptorInput(
        String branchName,
        String descriptor,
        String transactionUuid,
        String clientId
) {
}

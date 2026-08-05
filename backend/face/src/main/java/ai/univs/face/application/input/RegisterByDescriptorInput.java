package ai.univs.face.application.input;

/**
 * descriptor 기반 등록 입력 (UG-279).
 *
 * <p>{@link RegisterInput} 과 달리 {@code faceImage}, {@code checkLiveness},
 * {@code checkMultiFace} 가 없다. descriptor 가 존재한다는 것은 추출 단계가 이미 끝났다는 뜻이므로
 * 라이브니스/다중 얼굴 검사 대상이 되는 이미지가 존재하지 않는다.
 */
public record RegisterByDescriptorInput(
        String branchName,
        String descriptor,
        String transactionUuid,
        String clientId
) {
}

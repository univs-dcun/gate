package ai.univs.face.application.input;

/**
 * 특징점 기반 1:N 후보 목록 매칭 입력 (UG-314).
 *
 * <p>{@link IdentifyByDescriptorInput} 에 {@code threshold} 와 {@code maxCandidates} 가 붙는다.
 *
 * <p>{@code threshold} 는 <b>0.0 ~ 1.0 도메인 스케일</b>이다. 클라이언트가 보내는 백분율은 gate
 * 가 이 스케일로 바꿔서 넘긴다.
 */
public record IdentifyCandidatesByDescriptorInput(
        String branchName,
        String descriptor,
        double threshold,
        int maxCandidates,
        String transactionUuid,
        String clientId
) {
}

package ai.univs.face.application.result;

import java.util.List;

/**
 * 특징점 기반 1:N 후보 목록 매칭 결과 (UG-314).
 *
 * <p>{@code candidates} 는 임계치를 넘은 후보만, 유사도가 높은 순으로 담긴다. 비어 있을 수 있고
 * 그때 {@code result} 는 false 다 — <b>후보 1명 이상이 성공</b>이다.
 */
public record IdentifyCandidatesResult(
        String transactionUuid,
        List<Candidate> candidates,
        String threshold,
        boolean result
) {

    public record Candidate(
            String faceId,
            String similarity
    ) {
    }
}

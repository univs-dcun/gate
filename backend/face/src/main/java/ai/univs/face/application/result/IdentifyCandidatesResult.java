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
        /**
         * 임계치 통과 여부와 무관하게 <b>가장 가까웠던</b> 후보의 유사도. 후보가 아예 없으면
         * {@code null}.
         *
         * <p>{@code candidates} 가 비었을 때 호출자(gate)가 이력에 남길 값이다. 0 으로 눕히면
         * "아무도 근접하지 않았다" 와 "84.9 로 아깝게 미달했다" 가 이력에서 같아 보인다 —
         * 기존 1:N 은 그 값을 남기고 있어서, 안 내려보내면 이 API 만 조용히 후퇴한다.
         */
        String nearestSimilarity,
        String threshold,
        boolean result
) {

    public record Candidate(
            String faceId,
            String similarity
    ) {
    }
}

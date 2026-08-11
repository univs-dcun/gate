package ai.univs.match.api.dto;

import ai.univs.match.application.result.IdentifyCandidatesResult;
import ai.univs.match.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 1:N 후보 목록 매칭 응답 (UG-314).
 *
 * <p>유사도가 높은 순이다. 비어 있을 수 있다 — 갤러리에 등록된 특징점이 요청한 개수보다 적으면
 * 그만큼만 나온다.
 */
public record IdentifyCandidatesResponseDTO(
        @Schema(description = "유사도가 높은 순으로 정렬된 후보 목록")
        List<Candidate> candidates
) {

    public record Candidate(
            @Schema(description = SwaggerDescriptions.FACE_ID)
            String faceId,

            @Schema(description = SwaggerDescriptions.SIMILARITY)
            String similarity
    ) {
    }

    public static IdentifyCandidatesResponseDTO from(IdentifyCandidatesResult result) {
        return new IdentifyCandidatesResponseDTO(
                result.candidates().stream()
                        .map(candidate -> new Candidate(candidate.faceId(), candidate.similarity()))
                        .toList());
    }
}

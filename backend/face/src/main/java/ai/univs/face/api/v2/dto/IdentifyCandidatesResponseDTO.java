package ai.univs.face.api.v2.dto;

import ai.univs.face.application.result.IdentifyCandidatesResult;
import ai.univs.face.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 특징점 기반 1:N 후보 목록 매칭 응답 (UG-314).
 */
public record IdentifyCandidatesResponseDTO(
        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid,

        @Schema(description = "임계치를 넘은 후보 목록. 유사도가 높은 순이며 비어 있을 수 있다")
        List<Candidate> candidates,

        @Schema(description = SwaggerDescriptions.MATCH_THRESHOLD)
        String threshold,

        @Schema(description = "후보가 1명 이상이면 true")
        boolean result
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
                result.transactionUuid(),
                result.candidates().stream()
                        .map(candidate -> new Candidate(candidate.faceId(), candidate.similarity()))
                        .toList(),
                result.threshold(),
                result.result());
    }
}

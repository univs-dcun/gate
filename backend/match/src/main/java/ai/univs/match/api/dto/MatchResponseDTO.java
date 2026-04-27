package ai.univs.match.api.dto;

import ai.univs.match.application.result.MatchResult;
import ai.univs.match.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record MatchResponseDTO(
        @Schema(description = SwaggerDescriptions.BRANCH_NAME)
        String branchName,

        @Schema(description = SwaggerDescriptions.FACE_ID)
        String faceId
) {

    public static MatchResponseDTO from(MatchResult matchResult) {
        return new MatchResponseDTO(matchResult.branchName(), matchResult.faceId());
    }
}

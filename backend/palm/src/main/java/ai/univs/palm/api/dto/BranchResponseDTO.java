package ai.univs.palm.api.dto;

import ai.univs.palm.application.result.RegisterBranchResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record BranchResponseDTO(
        @Schema(description = "생성된 Watchlist UUID (= branchId)")
        String branchId
) {

    public static BranchResponseDTO from(RegisterBranchResult result) {
        return new BranchResponseDTO(result.branchId());
    }
}

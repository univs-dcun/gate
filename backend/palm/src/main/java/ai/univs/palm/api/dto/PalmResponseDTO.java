package ai.univs.palm.api.dto;

import ai.univs.palm.application.result.DeleteResult;
import ai.univs.palm.application.result.RegisterResult;
import ai.univs.palm.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record PalmResponseDTO(
        @Schema(description = SwaggerDescriptions.BRANCH_NAME)
        String branchName,

        @Schema(description = SwaggerDescriptions.PALM_ID)
        String palmId,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid
) {

        public static PalmResponseDTO from(RegisterResult result) {
                return new PalmResponseDTO(result.branchName(), result.palmId(), result.transactionUuid());
        }

        public static PalmResponseDTO from(DeleteResult result) {
                return new PalmResponseDTO(result.branchName(), result.palmId(), result.transactionUuid());
        }
}

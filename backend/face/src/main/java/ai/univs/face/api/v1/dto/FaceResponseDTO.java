package ai.univs.face.api.v1.dto;

import ai.univs.face.application.result.DeleteResult;
import ai.univs.face.application.result.RegisterResult;
import ai.univs.face.application.result.UpdateResult;
import ai.univs.face.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record FaceResponseDTO(
        @Schema(description = SwaggerDescriptions.BRANCH_NAME)
        String branchName,

        @Schema(description = SwaggerDescriptions.FACE_ID)
        String faceId,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid
) {

        public static FaceResponseDTO from(RegisterResult result) {
                return new FaceResponseDTO(result.branchName(), result.faceId(), result.transactionUuid());
        }

        public static FaceResponseDTO from(UpdateResult result) {
                return new FaceResponseDTO(result.branchName(), result.faceId(), result.transactionUuid());
        }

        public static FaceResponseDTO from(DeleteResult result) {
                return new FaceResponseDTO(result.branchName(), result.faceId(), result.transactionUuid());
        }
}

package ai.univs.gate.modules.feature.api.dto.face;

import ai.univs.gate.modules.feature.application.input.face.VerifyByImageInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.utils.ValidImageFile;
import ai.univs.gate.shared.web.enums.CallerType;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import org.springframework.web.multipart.MultipartFile;

public record VerifyByImageRequestDTO(
        @Schema(description = SwaggerDescriptions.TARGET_MATCHING_FEATURE_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
        @NotNull(message = "REQUIRED_IMAGE_FILE")
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile documentImage,

        @Schema(description = SwaggerDescriptions.MATCHING_FACE_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
        @NotNull(message = "REQUIRED_IMAGE_FILE")
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile matchingFeatureImage,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

        // UG-274: 두 번째 파라미터는 VerifyByImageInput.apiKey 로 들어간다. 이름이 timezone 으로
        // 잘못 선언돼 있어 호출 측(FaceController)이 ctx.getTimezone() 을 넘기도록 유도했고,
        // 그 결과 findByApiKey("Asia/Seoul") 로 API_KEY_NOT_FOUND 가 상시 발생했다.
        public VerifyByImageInput toVerifyByImageInput(Long accountId, String apiKey) {
                return new VerifyByImageInput(
                        CallerType.API,
                        accountId,
                        apiKey,
                        documentImage,
                        matchingFeatureImage,
                        TransactionUtil.useOrCreate(transactionUuid));
        }
}

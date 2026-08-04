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

        // UG-274: 두 번째 파라미터는 VerifyByImageInput.apiKey 로 들어가는데 이름이 timezone 으로
        // 잘못 선언돼 있었다. 이름을 맞춰 두어야 다음 사람이 호출부에 무엇을 넘겨야 하는지 알 수 있다.
        //
        // 다만 이 오기가 버그의 원인은 아니다. 같은 결함이 있던 LivenessRequestDTO 는 처음부터
        // apiKey 로 올바르게 선언돼 있었는데도 호출부가 ctx.getTimezone() 을 넘겼다. 실제 원인은
        // apiKey 와 timezone 이 둘 다 String 이라 인자를 바꿔 넣어도 컴파일이 통과한다는 것이고,
        // 그래서 재발 방지 장치는 이름 정정이 아니라 ApiKeyPropagationTest 다.
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

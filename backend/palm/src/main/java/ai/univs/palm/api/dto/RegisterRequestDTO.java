package ai.univs.palm.api.dto;

import ai.univs.palm.application.input.RegisterInput;
import ai.univs.palm.shared.swagger.SwaggerDescriptions;
import ai.univs.palm.shared.utils.ValidImageFile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.Length;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public record RegisterRequestDTO(
        @Schema(description = SwaggerDescriptions.BRANCH_NAME, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_BRANCH_NAME")
        @Length(max = 255, message = "INVALID_BRANCH_NAME_LENGTH")
        String branchName,

        @Schema(description = SwaggerDescriptions.PALM_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile palmImage,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid,

        @Schema(description = SwaggerDescriptions.CLIENT_ID)
        String clientId,

        @Schema(description = SwaggerDescriptions.CHECK_LIVENESS)
        Boolean checkLiveness,

        /**
         * 호출자가 발급한 팜 식별자 (UG-337). 주지 않거나 빈 값이면 지금처럼 이 서버가 발급한다.
         *
         * <p>gate 가 id 를 먼저 발급해 자기 이력에 남기고 그 id 로 등록한다. 원격 등록 뒤 gate 쪽
         * 쓰기가 실패해도 무엇을 되돌릴지 알게 하려는 것이다(UG-338). UUID 형식만 받는다 — 이
         * 서버가 스스로 발급하는 값과 같은 모양이다.
         */
        @Schema(description = "호출자가 발급한 팜 식별자(UUID). 주지 않으면 서버가 발급한다 (UG-337)")
        @Pattern(regexp = "^$|^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "INVALID_PALM_ID_FORMAT")
        String palmId
) {

    public RegisterInput toV2RegisterInput() {
        return new RegisterInput(
                StringUtils.hasText(palmId) ? palmId : "",
                palmImage,
                branchName,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString(),
                StringUtils.hasText(clientId) ? clientId : "SYSTEM",
                checkLiveness != null ? checkLiveness : true);
    }
}

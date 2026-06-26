package ai.univs.gate.modules.palm_feature.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@AllArgsConstructor
public class RegisterPalmFeignRequestDTO {

    private String branchName;
    @JsonProperty("palmImage")
    private MultipartFile featureImage;
    private String transactionUuid;
    private String clientId;
    private Boolean checkLiveness;
}

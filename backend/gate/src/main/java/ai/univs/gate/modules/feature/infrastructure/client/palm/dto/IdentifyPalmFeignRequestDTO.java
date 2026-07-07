package ai.univs.gate.modules.feature.infrastructure.client.palm.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@AllArgsConstructor
public class IdentifyPalmFeignRequestDTO {

    private String branchName;
    private MultipartFile palmImage;
    private String transactionUuid;
    private String clientId;
    private Boolean checkLiveness;
}

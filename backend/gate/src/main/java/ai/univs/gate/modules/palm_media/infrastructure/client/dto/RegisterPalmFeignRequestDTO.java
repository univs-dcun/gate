package ai.univs.gate.modules.palm_media.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@AllArgsConstructor
public class RegisterPalmFeignRequestDTO {
    private String branchName;
    private MultipartFile palmImage;
    private String transactionUuid;
    private String clientId;
    private Boolean checkLiveness;
}

package ai.univs.gate.modules.user.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFeignRequestDTO {

    private String branchName;
    private MultipartFile faceImage;
    private String transactionUuid;
    private String clientId;
    private boolean checkLiveness;
    private boolean checkMultiFace;
}

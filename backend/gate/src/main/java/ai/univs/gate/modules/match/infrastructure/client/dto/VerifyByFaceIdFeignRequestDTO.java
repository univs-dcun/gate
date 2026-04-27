package ai.univs.gate.modules.match.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyByFaceIdFeignRequestDTO {

    private String branchName;
    private String faceId;
    private MultipartFile faceImage;
    private String transactionUuid;
    private String clientId;
    private boolean checkLiveness;
    private boolean checkMultiFace;
}

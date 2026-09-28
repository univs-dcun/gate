package ai.univs.gate.modules.feature.infrastructure.client.face.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFaceFeignRequestDTO {

    private String branchName;
    private MultipartFile faceImage;
    private String transactionUuid;
    private String clientId;
    private boolean checkLiveness;
    private boolean checkMultiFace;

    /**
     * gate 가 발급한 특징점 id (UG-338). face v2 등록이 받는다(UG-337). 주지 않으면 하위 서비스가 발급한다.
     */
    private String faceId;
}

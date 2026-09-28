package ai.univs.gate.modules.feature.infrastructure.client.palm.dto;

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

    /**
     * gate 가 발급한 특징점 id (UG-338). palm 등록이 받는다(UG-337). 주지 않으면 하위 서비스가 발급한다.
     */
    private String palmId;
}

package ai.univs.gate.modules.palm_media.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DeletePalmFeignRequestDTO {
    private String branchName;
    private String palmId;
    private String transactionUuid;
    private String clientId;
}

package ai.univs.gate.modules.user.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteFeignRequestDTO {
    private String branchName;
    private String faceId;
    private String transactionUuid;
    private String clientId;
}

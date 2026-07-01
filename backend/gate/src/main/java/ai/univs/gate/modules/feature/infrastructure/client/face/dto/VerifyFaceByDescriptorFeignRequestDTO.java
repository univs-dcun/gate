package ai.univs.gate.modules.feature.infrastructure.client.face.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyFaceByDescriptorFeignRequestDTO {

    private String descriptor;
    private String targetDescriptor;
    private String transactionUuid;
    private String clientId;
}

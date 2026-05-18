package ai.univs.gate.modules.match.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyByDescriptorFeignRequestDTO {

    private String descriptor;
    private String targetDescriptor;
    private String transactionUuid;
    private String clientId;
}

package ai.univs.gate.modules.feature.infrastructure.client.face.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * descriptor 기반 1:N 매칭 요청 (UG-279).
 *
 * <p>사유는 {@link CreateFaceByDescriptorFeignRequestDTO} 와 같다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentifyFaceByDescriptorFeignRequestDTO {

    private String branchName;
    private String descriptor;
    private String transactionUuid;
    private String clientId;
}

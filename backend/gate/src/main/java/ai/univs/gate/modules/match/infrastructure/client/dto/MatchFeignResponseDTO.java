package ai.univs.gate.modules.match.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchFeignResponseDTO {

    private String transactionUuid;
    // 1:1 (image:image) 에서는 반환되지 않습니다.
    private String faceId;
    private BigDecimal similarity;
    private boolean result;
    private String failureType;
}

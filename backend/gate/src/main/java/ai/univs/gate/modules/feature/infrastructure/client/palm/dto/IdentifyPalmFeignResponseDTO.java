package ai.univs.gate.modules.feature.infrastructure.client.palm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentifyPalmFeignResponseDTO {

    private String transactionUuid;
    private String palmId;
    private String similarity;
    private String threshold;
    private boolean result;
}

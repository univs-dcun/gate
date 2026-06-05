package ai.univs.gate.modules.palm_feature.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentifyPalmFeignResponseDTO {
    private String transactionUuid;
    private String palmId;
    private String similarity;
    private String threshold;
    private boolean result;
}

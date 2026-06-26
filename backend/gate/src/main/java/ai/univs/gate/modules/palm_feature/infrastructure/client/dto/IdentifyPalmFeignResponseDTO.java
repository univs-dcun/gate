package ai.univs.gate.modules.palm_feature.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("palmId")
    private String featureId;
    private String similarity;
    private String threshold;
    private boolean result;
}

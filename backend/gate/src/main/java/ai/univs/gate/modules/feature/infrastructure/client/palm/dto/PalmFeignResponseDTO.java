package ai.univs.gate.modules.feature.infrastructure.client.palm.dto;

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
public class PalmFeignResponseDTO {

    private String branchName;
    @JsonProperty("palmId")
    private String featureId;
    private String transactionUuid;
}

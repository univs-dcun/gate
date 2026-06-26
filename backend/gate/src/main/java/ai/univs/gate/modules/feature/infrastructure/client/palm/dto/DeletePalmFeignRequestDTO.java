package ai.univs.gate.modules.feature.infrastructure.client.palm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DeletePalmFeignRequestDTO {

    private String branchName;
    @JsonProperty("palmId")
    private String featureId;
    private String transactionUuid;
    private String clientId;
}

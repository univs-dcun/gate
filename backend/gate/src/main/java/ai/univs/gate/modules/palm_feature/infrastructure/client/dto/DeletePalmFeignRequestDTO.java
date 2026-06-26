package ai.univs.gate.modules.palm_feature.infrastructure.client.dto;

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

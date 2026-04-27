package ai.univs.face.infrastructure.feign.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LivenessBodyFeignResponseDTO {

    private String probability;
    private int prdioction;
    @JsonProperty("prdioction_desc")
    private String prdioctionDesc;
    private String quality;
    private String threshold;
}

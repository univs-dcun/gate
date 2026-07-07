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
public class LivenessPalmFeignResponseDTO {

    private boolean success;
    private double score;
    private double threshold;
    private String message;
}

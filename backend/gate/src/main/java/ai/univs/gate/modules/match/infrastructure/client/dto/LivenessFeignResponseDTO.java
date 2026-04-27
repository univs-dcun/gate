package ai.univs.gate.modules.match.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LivenessFeignResponseDTO {

    private boolean success;
    private String probability;
    private int prdioction;
    private String prdioctionDesc;
    private String quality;
    private String threshold;
}

package ai.univs.face.infrastructure.feign.match.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateFeignRequestDTO {

    private String branchName;
    private String faceId;
    private String descriptor;
}

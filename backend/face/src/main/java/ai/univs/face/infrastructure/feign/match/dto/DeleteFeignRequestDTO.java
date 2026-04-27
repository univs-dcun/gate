package ai.univs.face.infrastructure.feign.match.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteFeignRequestDTO {

    private String branchName;
    private String faceId;
}

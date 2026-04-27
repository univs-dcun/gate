package ai.univs.face.infrastructure.feign.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExtractFeignResponseDTO {

    private ExtractBodyFeignResponseDTO extract;
    private LivenessBodyFeignResponseDTO liveness;
    @JsonProperty("face_count")
    private int faceCount;
}

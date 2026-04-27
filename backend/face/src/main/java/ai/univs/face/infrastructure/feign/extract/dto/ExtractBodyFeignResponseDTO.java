package ai.univs.face.infrastructure.feign.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExtractBodyFeignResponseDTO {

    @JsonProperty("face_image")
    private String faceImage;
    private String descriptor;
}

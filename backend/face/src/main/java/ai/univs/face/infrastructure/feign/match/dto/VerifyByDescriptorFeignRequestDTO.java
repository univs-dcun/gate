package ai.univs.face.infrastructure.feign.match.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyByDescriptorFeignRequestDTO {

    private String descriptor;
    private String targetDescriptor;
}

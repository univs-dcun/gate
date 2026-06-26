package ai.univs.gate.modules.feature.infrastructure.client.palm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@AllArgsConstructor
public class LivenessPalmFeignRequestDTO {

    @JsonProperty("palmImage")
    private MultipartFile featureImage;
    private String transactionUuid;
    private String clientId;
}

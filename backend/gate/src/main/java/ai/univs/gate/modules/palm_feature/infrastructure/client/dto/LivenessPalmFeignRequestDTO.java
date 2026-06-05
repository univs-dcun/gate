package ai.univs.gate.modules.palm_feature.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@AllArgsConstructor
public class LivenessPalmFeignRequestDTO {
    private MultipartFile palmImage;
    private String transactionUuid;
    private String clientId;
}

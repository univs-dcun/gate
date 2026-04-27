package ai.univs.auth.infrastructure.client.gate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitCompanyFeignRequestDTO {
    private Long accountId;
    private String managerMail;
}

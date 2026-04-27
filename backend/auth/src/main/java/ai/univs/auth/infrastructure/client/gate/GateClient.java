package ai.univs.auth.infrastructure.client.gate;

import ai.univs.auth.infrastructure.client.gate.dto.InitCompanyFeignRequestDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "gate-service")
public interface GateClient {

    @PostMapping("/api/v1/company/internal/init")
    void initCompany(@RequestBody InitCompanyFeignRequestDTO request);
}

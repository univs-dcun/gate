package ai.univs.gate.modules.palm_media.infrastructure.client;

import ai.univs.gate.modules.palm_media.infrastructure.client.dto.*;
import ai.univs.gate.support.feign.CommonFeignConfig;
import ai.univs.gate.support.feign.dto.FeignResponseApi;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "palm-service",
        contextId = "palmClient",
        configuration = CommonFeignConfig.class
)
public interface PalmClient {

    @PostMapping(value = "/api/v1/palm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FeignResponseApi<PalmFeignResponseDTO> register(@ModelAttribute RegisterPalmFeignRequestDTO request);

    @PostMapping(value = "/api/v1/palm/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    FeignResponseApi<PalmFeignResponseDTO> delete(@RequestBody DeletePalmFeignRequestDTO request);

    @PostMapping(value = "/api/v1/palm/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FeignResponseApi<IdentifyPalmFeignResponseDTO> identify(@ModelAttribute IdentifyPalmFeignRequestDTO request);

    @PostMapping(value = "/api/v1/palm/liveness", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FeignResponseApi<LivenessPalmFeignResponseDTO> liveness(@ModelAttribute LivenessPalmFeignRequestDTO request);
}

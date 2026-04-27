package ai.univs.gate.modules.user.infrastructure.client;

import ai.univs.gate.modules.user.infrastructure.client.dto.CreateFeignRequestDTO;
import ai.univs.gate.modules.user.infrastructure.client.dto.DeleteFeignRequestDTO;
import ai.univs.gate.modules.user.infrastructure.client.dto.FaceFeignResponseDTO;
import ai.univs.gate.modules.user.infrastructure.client.dto.UpdateFeignRequestDTO;
import ai.univs.gate.support.feign.CommonFeignConfig;
import ai.univs.gate.support.feign.dto.FeignResponseApi;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(
        name = "face-service",
        contextId = "faceUserClient",
        configuration = CommonFeignConfig.class
)
public interface FaceUserClient {

    @PostMapping(value = "/api/v2/face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FeignResponseApi<FaceFeignResponseDTO> createWithoutFaceId(@ModelAttribute CreateFeignRequestDTO request);

    @PutMapping(value = "/api/v1/face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FeignResponseApi<FaceFeignResponseDTO> update(@ModelAttribute UpdateFeignRequestDTO request);

    @PostMapping(value = "/api/v1/face/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    FeignResponseApi<FaceFeignResponseDTO> delete(DeleteFeignRequestDTO request);
}

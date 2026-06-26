package ai.univs.gate.modules.feature.infrastructure.client.face;

import ai.univs.gate.modules.feature.infrastructure.client.face.dto.CreateFaceFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.DeleteFaceFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.FaceFeignResponseDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.UpdateFaceFeignRequestDTO;
import ai.univs.gate.support.feign.CommonFeignConfig;
import ai.univs.gate.support.feign.dto.FeignResponseApi;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(
        name = "face-service",
        contextId = "faceFeatureClient",
        configuration = CommonFeignConfig.class
)
public interface FaceFeatureClient {

    @PostMapping(value = "/api/v2/face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FeignResponseApi<FaceFeignResponseDTO> createWithoutFaceId(@ModelAttribute CreateFaceFeignRequestDTO request);

    @PutMapping(value = "/api/v1/face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FeignResponseApi<FaceFeignResponseDTO> update(@ModelAttribute UpdateFaceFeignRequestDTO request);

    @PostMapping(value = "/api/v1/face/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    FeignResponseApi<FaceFeignResponseDTO> delete(DeleteFaceFeignRequestDTO request);
}

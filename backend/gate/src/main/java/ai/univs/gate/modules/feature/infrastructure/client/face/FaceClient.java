package ai.univs.gate.modules.feature.infrastructure.client.face;

import ai.univs.gate.modules.feature.infrastructure.client.face.dto.*;
import ai.univs.gate.support.feign.CommonFeignConfig;
import ai.univs.gate.support.feign.dto.FeignResponseApi;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "face-service",
        configuration = CommonFeignConfig.class
)
public interface FaceClient {

    @PostMapping(value = "/api/v2/face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FeignResponseApi<FaceFeignResponseDTO> create(@ModelAttribute CreateFaceFeignRequestDTO request);

    @PutMapping(value = "/api/v1/face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FeignResponseApi<FaceFeignResponseDTO> update(@ModelAttribute UpdateFaceFeignRequestDTO request);

    @PostMapping(value = "/api/v1/face/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    FeignResponseApi<FaceFeignResponseDTO> delete(DeleteFaceFeignRequestDTO request);

    @PostMapping(value = "/api/v2/face/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FeignResponseApi<ExtractFaceFeignResponseDTO> extract(@ModelAttribute ExtractFaceFeignRequestDTO request);

    @PostMapping(value = "/api/v1/face/verify/id", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FeignResponseApi<MatchFaceFeignResponseDTO> verifyByFaceId(@ModelAttribute VerifyFaceByFaceIdFeignRequestDTO request);

    @PostMapping(value = "/api/v1/face/verify/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FeignResponseApi<MatchFaceFeignResponseDTO> verifyByImage(@ModelAttribute VerifyFaceByImageFeignRequestDTO request);

    @PostMapping(value = "/api/v1/face/verify/descriptor", consumes = MediaType.APPLICATION_JSON_VALUE)
    FeignResponseApi<VerifyFaceByDescriptorFeignResponseDTO> verifyDescriptor(@RequestBody VerifyFaceByDescriptorFeignRequestDTO request);

    @PostMapping(value = "/api/v1/face/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FeignResponseApi<MatchFaceFeignResponseDTO> identify(@ModelAttribute IdentifyFaceFeignRequestDTO request);

    @PostMapping(value = "/api/v2/face/liveness", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FeignResponseApi<LivenessFaceFeignResponseDTO> liveness(@ModelAttribute LivenessFaceFeignRequestDTO request);
}

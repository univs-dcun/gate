package ai.univs.gate.modules.match.infrastructure.client;

import ai.univs.gate.modules.match.infrastructure.client.dto.*;
import ai.univs.gate.support.feign.CommonFeignConfig;
import ai.univs.gate.support.feign.dto.FeignResponseApi;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
        name = "face-service",
        contextId = "faceMatchClient",
        configuration = CommonFeignConfig.class
)
public interface FaceMatchClient {

    @PostMapping(value = "/api/v1/face/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FeignResponseApi<MatchFeignResponseDTO> identify(@ModelAttribute IdentifyFeignRequestDTO request);

    @PostMapping(value = "/api/v1/face/verify/id", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FeignResponseApi<MatchFeignResponseDTO> verifyByFaceId(@ModelAttribute VerifyByFaceIdFeignRequestDTO request);

    @PostMapping(value = "/api/v1/face/verify/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FeignResponseApi<MatchFeignResponseDTO> verifyByImage(@ModelAttribute VerifyByImageFeignRequestDTO request);

    @PostMapping(value = "/api/v2/face/liveness", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FeignResponseApi<LivenessFeignResponseDTO> liveness(@ModelAttribute LivenessFeignRequestDTO request);
}

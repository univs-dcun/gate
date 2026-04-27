package ai.univs.face.infrastructure.feign.match;

import ai.univs.face.infrastructure.feign.match.dto.*;
import ai.univs.face.shared.feign.CommonFeignConfig;
import ai.univs.face.shared.feign.dto.FeignResponseApi;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(
        name = "match-server",
        configuration = { CommonFeignConfig.class }
)
public interface MatchFeign {

    @PostMapping(value = "/api/v1/match/face-id", consumes = MediaType.APPLICATION_JSON_VALUE)
    FeignResponseApi<MatchFeignResponseDTO> registerWithFaceId(RegisterFeignRequestDTO request);

    @PostMapping(value = "/api/v1/match", consumes = MediaType.APPLICATION_JSON_VALUE)
    FeignResponseApi<MatchFeignResponseDTO> register(RegisterV2FeignRequestDTO request);

    @PutMapping(value = "/api/v1/match", consumes = MediaType.APPLICATION_JSON_VALUE)
    FeignResponseApi<MatchFeignResponseDTO> update(UpdateFeignRequestDTO request);

    @PostMapping(value = "/api/v1/match/delete")
    FeignResponseApi<MatchFeignResponseDTO> delete(DeleteFeignRequestDTO request);

    @PostMapping(value = "/api/v1/match/identify", consumes = MediaType.APPLICATION_JSON_VALUE)
    FeignResponseApi<IdentifyFeignResponseDTO> identify(IdentifyFeignRequestDTO request);

    @PostMapping(value = "/api/v1/match/verify/id", consumes = MediaType.APPLICATION_JSON_VALUE)
    FeignResponseApi<VerifyFeignResponseDTO> verifyById(VerifyByIdFeignRequestDTO request);

    @PostMapping(value = "/api/v1/match/verify/descriptor", consumes = MediaType.APPLICATION_JSON_VALUE)
    FeignResponseApi<VerifyFeignResponseDTO> verifyByDescriptor(VerifyByDescriptorFeignRequestDTO request);
}

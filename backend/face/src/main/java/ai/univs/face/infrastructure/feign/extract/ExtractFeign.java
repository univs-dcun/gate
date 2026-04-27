package ai.univs.face.infrastructure.feign.extract;

import ai.univs.face.shared.feign.CommonFeignConfig;
import ai.univs.face.infrastructure.feign.extract.dto.ExtractFeignResponseDTO;
import ai.univs.face.infrastructure.feign.extract.dto.ExtractFeignResponseApi;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(
        name = "fxp-preprocess-service",
        configuration = { CommonFeignConfig.class }
)
public interface ExtractFeign {

    @PostMapping(value = "/liveness/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ExtractFeignResponseApi<ExtractFeignResponseDTO> extractWithOptionalLivenessAndMultiFace(
            @RequestParam(name = "face-image") int faceImage,
            @RequestParam(name = "image_rotate") int imageRotate,
            @RequestParam(name = "descriptor") int descriptor,
            @RequestParam(name = "liveness") int liveness,
            @RequestParam(name = "check_multi_face") int checkMultiFace,
            @RequestPart(name = "image") MultipartFile image
    );
}

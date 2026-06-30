package ai.univs.gate.support.face;

import ai.univs.gate.modules.feature.infrastructure.client.face.FaceFeatureClient;
import ai.univs.gate.modules.feature.infrastructure.client.face.FaceMatchClient;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FaceService {

    private final FaceFeatureClient faceUserClient;
    private final FaceMatchClient faceMatchClient;

    public String createFace(CreateFaceFeignRequestDTO request) {
        return faceUserClient.createWithoutFaceId(request)
                .getData()
                .getFaceId();
    }

    public void updateFace(UpdateFaceFeignRequestDTO request) {
        faceUserClient.update(request);
    }

    public void deleteFace(DeleteFaceFeignRequestDTO request) {
        faceUserClient.delete(request);
    }

    public MatchFaceFeignResponseDTO identify(IdentifyFaceFeignRequestDTO feignRequest) {
        return faceMatchClient.identify(feignRequest)
                .getData();
    }

    public MatchFaceFeignResponseDTO verifyByFaceId(VerifyFaceByFaceIdFeignRequestDTO feignRequest) {
        return faceMatchClient.verifyByFaceId(feignRequest)
                .getData();
    }

    public MatchFaceFeignResponseDTO verifyByImage(VerifyFaceByImageFeignRequestDTO feignRequest) {
        return faceMatchClient.verifyByImage(feignRequest)
                .getData();
    }

    public LivenessFaceFeignResponseDTO liveness(LivenessFaceFeignRequestDTO feignRequest) {
        return faceMatchClient.liveness(feignRequest)
                .getData();
    }

    public ExtractFaceFeignResponseDTO extract(ExtractFaceFeignRequestDTO feignRequest) {
        return faceMatchClient.extract(feignRequest)
                .getData();
    }

    public VerifyFaceByDescriptorFeignResponseDTO verifyDescriptor(VerifyFaceByDescriptorFeignRequestDTO feignRequest) {
        return faceMatchClient.verifyDescriptor(feignRequest)
                .getData();
    }
}

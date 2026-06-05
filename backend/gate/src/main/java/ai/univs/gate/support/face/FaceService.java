package ai.univs.gate.support.face;

import ai.univs.gate.modules.face_feature.infrastructure.client.FaceFeatureClient;
import ai.univs.gate.modules.face_feature.infrastructure.client.dto.*;
import ai.univs.gate.modules.face_feature.infrastructure.client.FaceMatchClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FaceService {

    private final FaceFeatureClient faceUserClient;
    private final FaceMatchClient faceMatchClient;

    public String createFace(CreateFeignRequestDTO request) {
        return faceUserClient.createWithoutFaceId(request)
                .getData()
                .getFaceId();
    }

    public void updateFace(UpdateFeignRequestDTO request) {
        faceUserClient.update(request);
    }

    public void deleteFace(DeleteFeignRequestDTO request) {
        faceUserClient.delete(request);
    }

    public MatchFeignResponseDTO identify(IdentifyFeignRequestDTO feignRequest) {
        return faceMatchClient.identify(feignRequest)
                .getData();
    }

    public MatchFeignResponseDTO verifyByFaceId(VerifyByFaceIdFeignRequestDTO feignRequest) {
        return faceMatchClient.verifyByFaceId(feignRequest)
                .getData();
    }

    public MatchFeignResponseDTO verifyByImage(VerifyByImageFeignRequestDTO feignRequest) {
        return faceMatchClient.verifyByImage(feignRequest)
                .getData();
    }

    public LivenessFeignResponseDTO liveness(LivenessFeignRequestDTO feignRequest) {
        return faceMatchClient.liveness(feignRequest)
                .getData();
    }

    public ExtractFeignResponseDTO extract(ExtractFeignRequestDTO feignRequest) {
        return faceMatchClient.extract(feignRequest)
                .getData();
    }

    public VerifyByDescriptorFeignResponseDTO verifyDescriptor(VerifyByDescriptorFeignRequestDTO feignRequest) {
        return faceMatchClient.verifyDescriptor(feignRequest)
                .getData();
    }
}

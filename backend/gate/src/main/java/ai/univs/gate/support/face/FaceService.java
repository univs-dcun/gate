package ai.univs.gate.support.face;

import ai.univs.gate.modules.feature.infrastructure.client.face.FaceClient;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FaceService {

    private final FaceClient faceClient;

    public String createFace(CreateFaceFeignRequestDTO request) {
        return faceClient.createWithoutFaceId(request)
                .getData()
                .getFaceId();
    }

    public void updateFace(UpdateFaceFeignRequestDTO request) {
        faceClient.update(request);
    }

    public void deleteFace(DeleteFaceFeignRequestDTO request) {
        faceClient.delete(request);
    }

    public MatchFaceFeignResponseDTO identify(IdentifyFaceFeignRequestDTO feignRequest) {
        return faceClient.identify(feignRequest)
                .getData();
    }

    public MatchFaceFeignResponseDTO verifyByFaceId(VerifyFaceByFaceIdFeignRequestDTO feignRequest) {
        return faceClient.verifyByFaceId(feignRequest)
                .getData();
    }

    public MatchFaceFeignResponseDTO verifyByImage(VerifyFaceByImageFeignRequestDTO feignRequest) {
        return faceClient.verifyByImage(feignRequest)
                .getData();
    }

    public LivenessFaceFeignResponseDTO liveness(LivenessFaceFeignRequestDTO feignRequest) {
        return faceClient.liveness(feignRequest)
                .getData();
    }

    public ExtractFaceFeignResponseDTO extract(ExtractFaceFeignRequestDTO feignRequest) {
        return faceClient.extract(feignRequest)
                .getData();
    }

    public VerifyFaceByDescriptorFeignResponseDTO verifyDescriptor(VerifyFaceByDescriptorFeignRequestDTO feignRequest) {
        return faceClient.verifyDescriptor(feignRequest)
                .getData();
    }
}

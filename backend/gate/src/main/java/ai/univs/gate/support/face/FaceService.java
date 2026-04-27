package ai.univs.gate.support.face;

import ai.univs.gate.modules.match.infrastructure.client.FaceMatchClient;
import ai.univs.gate.modules.match.infrastructure.client.dto.*;
import ai.univs.gate.modules.user.infrastructure.client.FaceUserClient;
import ai.univs.gate.modules.user.infrastructure.client.dto.CreateFeignRequestDTO;
import ai.univs.gate.modules.user.infrastructure.client.dto.DeleteFeignRequestDTO;
import ai.univs.gate.modules.user.infrastructure.client.dto.UpdateFeignRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FaceService {

    private final FaceUserClient faceUserClient;
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
}

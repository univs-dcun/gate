package ai.univs.gate.support.feature.face;

import ai.univs.gate.modules.feature.infrastructure.client.face.FaceClient;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.*;
import ai.univs.gate.support.feign.RemoteCalls;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FaceService {

    private final FaceClient faceClient;

    /*
     * UG-280 반박 리뷰: 모든 호출을 RemoteCalls 로 감싼다.
     *
     * CommonErrorDecoder 는 상태 코드 300 이상의 "응답이 도착했을 때만" 불린다. 연결 거부·읽기
     * 타임아웃·연결 리셋은 응답이 없어 Feign 이 RetryableException 을 던지고, 이 프로젝트에는
     * Retryer 빈이 없으므로(기본값 NEVER_RETRY) 그대로 올라온다. 그 예외는 BusinessException
     * 계열이 아니라 매칭 UseCase 의 noRollbackFor 에 걸리지 않으므로, UG-280 이 고치려던 증상이
     * 가장 흔한 장애 형태(과부하로 응답하지 못하는 경우)에서 그대로 남아 있었다.
     */

    public String createFace(CreateFaceFeignRequestDTO request) {
        return RemoteCalls.data("face.createFace", () -> faceClient.create(request)).getFaceId();
    }

    public void updateFace(UpdateFaceFeignRequestDTO request) {
        RemoteCalls.run("face.updateFace", () -> faceClient.update(request));
    }

    public void deleteFace(DeleteFaceFeignRequestDTO request) {
        RemoteCalls.run("face.deleteFace", () -> faceClient.delete(request));
    }

    public MatchFaceFeignResponseDTO identify(IdentifyFaceFeignRequestDTO feignRequest) {
        return RemoteCalls.data("face.identify", () -> faceClient.identify(feignRequest));
    }

    public String createFaceByDescriptor(CreateFaceByDescriptorFeignRequestDTO request) {
        return RemoteCalls.data("face.createFaceByDescriptor", () -> faceClient.createByDescriptor(request)).getFaceId();
    }

    public MatchFaceFeignResponseDTO identifyByDescriptor(IdentifyFaceByDescriptorFeignRequestDTO feignRequest) {
        return RemoteCalls.data("face.identifyByDescriptor", () -> faceClient.identifyByDescriptor(feignRequest));
    }

    public MatchFaceFeignResponseDTO verifyByFaceId(VerifyFaceByFaceIdFeignRequestDTO feignRequest) {
        return RemoteCalls.data("face.verifyByFaceId", () -> faceClient.verifyByFaceId(feignRequest));
    }

    public MatchFaceFeignResponseDTO verifyByImage(VerifyFaceByImageFeignRequestDTO feignRequest) {
        return RemoteCalls.data("face.verifyByImage", () -> faceClient.verifyByImage(feignRequest));
    }

    public LivenessFaceFeignResponseDTO liveness(LivenessFaceFeignRequestDTO feignRequest) {
        return RemoteCalls.data("face.liveness", () -> faceClient.liveness(feignRequest));
    }

    public ExtractFaceFeignResponseDTO extract(ExtractFaceFeignRequestDTO feignRequest) {
        return RemoteCalls.data("face.extract", () -> faceClient.extract(feignRequest));
    }

    public VerifyFaceByDescriptorFeignResponseDTO verifyDescriptor(VerifyFaceByDescriptorFeignRequestDTO feignRequest) {
        return RemoteCalls.data("face.verifyDescriptor", () -> faceClient.verifyDescriptor(feignRequest));
    }
}

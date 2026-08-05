package ai.univs.gate.support.feature.palm;

import ai.univs.gate.modules.feature.infrastructure.client.palm.PalmClient;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.*;
import ai.univs.gate.support.feign.RemoteCalls;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PalmService {

    private final PalmClient palmClient;

    /*
     * UG-280 반박 리뷰: 모든 호출을 RemoteCalls 로 감싼다.
     *
     * CommonErrorDecoder 는 상태 코드 300 이상의 "응답이 도착했을 때만" 불린다. 연결 거부·읽기
     * 타임아웃·연결 리셋은 응답이 없어 Feign 이 RetryableException 을 던지고, 이 프로젝트에는
     * Retryer 빈이 없으므로(기본값 NEVER_RETRY) 그대로 올라온다. 그 예외는 BusinessException
     * 계열이 아니라 매칭 UseCase 의 noRollbackFor 에 걸리지 않으므로, UG-280 이 고치려던 증상이
     * 가장 흔한 장애 형태(과부하로 응답하지 못하는 경우)에서 그대로 남아 있었다.
     */

    public String registerPalm(RegisterPalmFeignRequestDTO request) {
        return RemoteCalls.of("palm.registerPalm", () -> palmClient.register(request).getData().getPalmId());
    }

    public void deletePalm(DeletePalmFeignRequestDTO request) {
        RemoteCalls.run("palm.deletePalm", () -> palmClient.delete(request));
    }

    public IdentifyPalmFeignResponseDTO identify(IdentifyPalmFeignRequestDTO request) {
        return RemoteCalls.of("palm.identify", () -> palmClient.identify(request).getData());
    }

    public LivenessPalmFeignResponseDTO liveness(LivenessPalmFeignRequestDTO request) {
        return RemoteCalls.of("palm.liveness", () -> palmClient.liveness(request).getData());
    }
}

package ai.univs.gate.support.palm;

import ai.univs.gate.modules.palm_media.infrastructure.client.PalmClient;
import ai.univs.gate.modules.palm_media.infrastructure.client.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PalmService {

    private final PalmClient palmClient;

    public String registerPalm(RegisterPalmFeignRequestDTO request) {
        return palmClient.register(request)
                .getData()
                .getPalmId();
    }

    public void deletePalm(DeletePalmFeignRequestDTO request) {
        palmClient.delete(request);
    }

    public IdentifyPalmFeignResponseDTO identify(IdentifyPalmFeignRequestDTO request) {
        return palmClient.identify(request).getData();
    }

    public LivenessPalmFeignResponseDTO liveness(LivenessPalmFeignRequestDTO request) {
        return palmClient.liveness(request).getData();
    }
}

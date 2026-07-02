package ai.univs.gate.shared.web;

import ai.univs.gate.shared.web.dto.CapabilitiesResponseDTO;
import ai.univs.gate.shared.web.dto.ResponseApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "서비스 기능 제공 여부")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/capabilities")
public class CapabilityController {

    private final GateFeatureProperties featureProperties;

    @Operation(summary = "생체인증 기능 제공 여부 조회", description = "설치 환경에서 제공되는 생체인증 modality(face/palm)를 반환합니다. 클라이언트는 이 값으로 노출할 기능을 결정할 수 있습니다.")
    @GetMapping
    public ResponseEntity<ResponseApi<CapabilitiesResponseDTO>> getCapabilities() {
        var response = new CapabilitiesResponseDTO(
                featureProperties.isFace(),
                featureProperties.isPalm());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

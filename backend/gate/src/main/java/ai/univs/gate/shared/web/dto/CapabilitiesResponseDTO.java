package ai.univs.gate.shared.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record CapabilitiesResponseDTO(
        @Schema(description = "얼굴 인증 기능 제공 여부")
        boolean face,

        @Schema(description = "손바닥 정맥 인증 기능 제공 여부")
        boolean palm
) {
}

package ai.univs.gate.modules.face_media.api.dto;

import ai.univs.gate.shared.web.dto.CustomPage;

import java.util.List;

public record FaceMediasResponseDTO(
        List<FaceMediaResponseDTO> users,
        CustomPage page
) {
}

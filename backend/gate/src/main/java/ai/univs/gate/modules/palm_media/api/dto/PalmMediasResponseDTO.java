package ai.univs.gate.modules.palm_media.api.dto;

import ai.univs.gate.shared.web.dto.CustomPage;

import java.util.List;

public record PalmMediasResponseDTO(
        List<PalmMediaResponseDTO> palmMedias,
        CustomPage page
) {}

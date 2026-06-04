package ai.univs.gate.facade.media.api.dto;

import ai.univs.gate.shared.web.dto.CustomPage;

import java.util.List;

public record MediaListResponse(
        List<MediaItemResponse> medias,
        CustomPage page
) {}

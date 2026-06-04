package ai.univs.gate.facade.media.application.result;

import ai.univs.gate.shared.usecase.result.CustomPageResult;

import java.util.List;

public record MediaListResult(
        List<MediaItemResult> medias,
        CustomPageResult page
) {}

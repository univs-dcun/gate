package ai.univs.gate.modules.palm_media.application.result;

import ai.univs.gate.shared.usecase.result.CustomPageResult;

import java.util.List;

public record GetPalmMediasResult(
        List<PalmMediaResult> palmMedias,
        CustomPageResult page
) {}

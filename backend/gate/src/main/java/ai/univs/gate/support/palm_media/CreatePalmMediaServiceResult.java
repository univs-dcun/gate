package ai.univs.gate.support.palm_media;

import ai.univs.gate.modules.palm_media.domain.entity.PalmMedia;

public record CreatePalmMediaServiceResult(
        PalmMedia palmMedia,
        boolean livenessChecked
) {}

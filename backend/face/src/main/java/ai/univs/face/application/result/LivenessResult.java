package ai.univs.face.application.result;

import ai.univs.face.infrastructure.feign.extract.dto.LivenessBodyFeignResponseDTO;

public record LivenessResult(
        boolean success,
        String probability,
        int prdioction,
        String prdioctionDesc,
        String quality,
        String threshold
) {

    public static LivenessResult from(LivenessBodyFeignResponseDTO dto) {
        return new LivenessResult(
                dto.getPrdioction() == 0,
                dto.getProbability(),
                dto.getPrdioction(),
                dto.getPrdioctionDesc(),
                dto.getQuality(),
                dto.getThreshold()
        );
    }
}
package ai.univs.gate.modules.match.application.result;

import ai.univs.gate.modules.palm_media.infrastructure.client.dto.LivenessPalmFeignResponseDTO;

public record PalmLivenessResult(
        boolean success,
        double score,
        double threshold,
        String message,
        String transactionUuid
) {

    public static PalmLivenessResult from(LivenessPalmFeignResponseDTO data, String transactionUuid) {
        return new PalmLivenessResult(
                data.isSuccess(),
                data.getScore(),
                data.getThreshold(),
                data.getMessage(),
                transactionUuid);
    }
}

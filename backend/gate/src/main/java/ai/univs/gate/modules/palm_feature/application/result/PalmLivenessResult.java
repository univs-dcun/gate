package ai.univs.gate.modules.palm_feature.application.result;

import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.LivenessPalmFeignResponseDTO;

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

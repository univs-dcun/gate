package ai.univs.gate.modules.match.application.result;

import ai.univs.gate.modules.match.infrastructure.client.dto.LivenessFeignResponseDTO;

public record LivenessResult(
        boolean success,
        String probability,
        int prdioction,
        String prdioctionDesc,
        String quality,
        String threshold,
        String transactionUuid
) {

    public static LivenessResult from(LivenessFeignResponseDTO data, String transactionUuid) {
        return new LivenessResult(
                data.isSuccess(),
                data.getProbability(),
                data.getPrdioction(),
                data.getPrdioctionDesc(),
                data.getQuality(),
                data.getThreshold(),
                transactionUuid);
    }
}

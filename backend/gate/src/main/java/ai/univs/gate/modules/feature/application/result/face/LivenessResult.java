package ai.univs.gate.modules.feature.application.result.face;

import ai.univs.gate.modules.feature.infrastructure.client.face.dto.LivenessFaceFeignResponseDTO;

public record LivenessResult(
        boolean success,
        String probability,
        int prdioction,
        String prdioctionDesc,
        String quality,
        String threshold,
        String transactionUuid,
        Boolean consentSnapshot
) {

    public static LivenessResult from(LivenessFaceFeignResponseDTO data, String transactionUuid, Boolean consentSnapshot) {
        return new LivenessResult(
                data.isSuccess(),
                data.getProbability(),
                data.getPrdioction(),
                data.getPrdioctionDesc(),
                data.getQuality(),
                data.getThreshold(),
                transactionUuid,
                consentSnapshot);
    }
}

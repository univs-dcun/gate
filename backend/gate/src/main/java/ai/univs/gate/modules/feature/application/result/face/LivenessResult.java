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

    /**
     * 응답 · 이력에 남길 실패 타입 (UG-391). 성공이면 빈 값, 실패면 엔진 사유(대문자), 사유가 비면 LIVENESS_FAILED —
     * 다른 매칭 API 의 failureType 과 같은 이름을 쓴다.
     */
    public String failureType() {
        if (success) return "";
        return prdioctionDesc != null && !prdioctionDesc.isBlank()
                ? prdioctionDesc.toUpperCase()
                : "LIVENESS_FAILED";
    }

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

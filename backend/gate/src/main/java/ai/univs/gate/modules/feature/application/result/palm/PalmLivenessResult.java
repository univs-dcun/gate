package ai.univs.gate.modules.feature.application.result.palm;

import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.LivenessPalmFeignResponseDTO;

import java.util.Locale;

public record PalmLivenessResult(
        boolean success,
        double score,
        double threshold,
        String message,
        String transactionUuid
) {

    /**
     * 응답 · 이력에 남길 실패 타입 (UG-393, 얼굴 {@code LivenessResult.failureType()} 과 같은 규칙).
     * 성공이면 빈 값, 사유가 없으면 LIVENESS_FAILED, 있으면 대문자 식별자.
     *
     * <p>palm 서비스는 엔진이 위조로 판정하면 사유 없이(null) 돌려주므로 그 경우가 LIVENESS_FAILED 다.
     * 사유가 오는 것은 엔진이 검사하지 못한 경우(손바닥 미검출 등)로, 엔진 오류 문장("No palm detected")이라
     * 공백을 {@code _} 로 바꿔 식별자로 만든다 — palm 서비스가 엔진 title 을 type 으로 바꾸는 방식과 같다.
     */
    public String failureType() {
        if (success) return "";
        return message != null && !message.isBlank()
                ? message.strip().toUpperCase(Locale.ROOT).replaceAll("\\s+", "_")
                : "LIVENESS_FAILED";
    }

    public static PalmLivenessResult from(LivenessPalmFeignResponseDTO data, String transactionUuid) {
        return new PalmLivenessResult(
                data.isSuccess(),
                data.getScore(),
                data.getThreshold(),
                data.getMessage(),
                transactionUuid);
    }
}

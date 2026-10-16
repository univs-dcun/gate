package ai.univs.gate.modules.feature.application.result.palm;

import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.LivenessPalmFeignResponseDTO;
import ai.univs.gate.shared.web.enums.LivenessErrorType;

import java.util.Locale;

public record PalmLivenessResult(
        boolean success,
        double score,
        double threshold,
        String message,
        String transactionUuid
) {

    /** 이력 failure_type 컬럼(100자)보다 넉넉히 짧게 — 엔진 문장이 길어도 기록이 실패하지 않게 */
    private static final int MAX_FAILURE_TYPE_LENGTH = 64;

    /**
     * 응답 · 이력에 남길 실패 타입 (UG-393, 얼굴 {@code LivenessResult.failureType()} 과 같은 규칙).
     * 성공이면 빈 값, 사유가 없으면 LIVENESS_FAILED, 있으면 대문자 식별자.
     *
     * <p>palm 서비스는 엔진이 위조로 판정하면 사유 없이(null) 돌려주므로 그 경우가 LIVENESS_FAILED 다.
     * 사유가 오는 것은 엔진이 검사하지 못한 경우(손바닥 미검출 등)로, 엔진 오류 응답의 detail 문장이다
     * (palm 서비스가 type 으로 쓰는 title 이 아니다). 자유 문장이라 영문 · 숫자가 아닌 글자를 {@code _} 로 바꾸고
     * 길이를 잘라 식별자로 만든다 (반박 리뷰: 마침표 · 가변 값이 그대로 응답에 나가지 않게).
     */
    public String failureType() {
        if (success) return "";
        if (message == null) return LivenessErrorType.UNSPECIFIED_REJECT;
        String type = message.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (type.isEmpty()) return LivenessErrorType.UNSPECIFIED_REJECT;
        return type.length() > MAX_FAILURE_TYPE_LENGTH
                ? type.substring(0, MAX_FAILURE_TYPE_LENGTH).replaceAll("_+$", "")
                : type;
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

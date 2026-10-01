package ai.univs.gate.modules.feature.application.result.face;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;

/**
 * 특징점 삭제 웹훅({@code FEATURE_DELETED})의 {@code data} (UG-345).
 *
 * <p>삭제 API 는 본문 없이 204 를 돌려주므로 "응답 data 와 같은 구조" 를 따를 수 없다. 수신 측이 자기 쪽
 * 기록을 찾는 데 쓰는 식별자만 싣는다 — 등록 웹훅의 {@link FaceFeatureResult} 와 같은 이름을 쓴다.
 *
 * @param transactionUuid 삭제 요청의 거래 ID. 특징점 이력의 삭제 행과 같다 (등록 때의 값이 아니다)
 */
public record FaceFeatureDeletedResult(
        Long faceFeatureId,
        Long projectId,
        String featureId,
        String externalKey,
        String transactionUuid
) {

    public static FaceFeatureDeletedResult from(BiometricFeature feature, Long projectId, String transactionUuid) {
        return new FaceFeatureDeletedResult(
                feature.getId(),
                projectId,
                feature.getFeatureId(),
                feature.getExternalKey(),
                transactionUuid);
    }
}

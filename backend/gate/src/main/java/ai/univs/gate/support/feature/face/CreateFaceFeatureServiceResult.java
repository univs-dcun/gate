package ai.univs.gate.support.feature.face;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;

public record CreateFaceFeatureServiceResult(
        BiometricFeature biometricFeature,
        boolean livenessChecked,
        // UG-336: 등록 시점의 동의 설정. 호출자가 커밋 뒤에 설정을 다시 조회하지 않게 한다.
        boolean consentEnabled
) {
}

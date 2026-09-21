package ai.univs.gate.modules.feature.domain.enums;

/**
 * 통합 이력의 사건 종류 (UG-326) — {@link MatchType} 과 {@link FeatureActionType} 의 합집합.
 *
 * <p>목록 API 의 {@code matchType} 필드는 이 값을 내려준다. 두 열거형을 합치지 않고 셋째를 둔 이유:
 * {@code match_history} 와 {@code feature_history} 는 축이 다른 테이블이라 각자의 열거형을 유지해야
 * 하고, 화면은 그 둘을 한 목록으로 보되 필터에서는 [인증] / [특징점 관리] 두 그룹으로 나눈다.
 */
public enum ActivityType {
    // ── 특징점 관리 (feature_history) ──
    REGISTER,
    DELETE,
    // ── 인증 (match_history) ──
    VERIFY,             // 레거시 (기존 데이터 보존용)
    VERIFY_ID,
    VERIFY_IMAGE,
    VERIFY_DESCRIPTOR,
    IDENTIFY,
    LIVENESS,
    ;
}

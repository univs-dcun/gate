package ai.univs.gate.modules.feature.domain.enums;

public enum MatchType {

    REGISTER,
    VERIFY,        // 레거시 (기존 데이터 보존용, 신규 저장 안 함)
    VERIFY_ID,     // /verify/id  - 촬영 인증
    VERIFY_IMAGE,  // /verify/image - 사진 인증
    // UG-279: /verify/descriptor. VERIFY_ID(촬영)·VERIFY_IMAGE(사진) 둘 다 이미지 기반이라 의미상
    // 재사용할 대상이 없어 신규 추가했다. 17자로 match_type VARCHAR(20) 에 들어가므로 마이그레이션
    // 은 불필요하다. DashboardStatsService 는 매칭 타입을 명시 열거로 집계하므로 이 타입은 대시보드
    // 지표에 잡히지 않는다 — FACE 에서 IMAGE/DESCRIPTOR 구분이 필요해지는 시점에 함께 정리한다.
    VERIFY_DESCRIPTOR,
    IDENTIFY,
    LIVENESS,
    ;
}

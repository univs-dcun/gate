package ai.univs.gate.modules.match.domain.enums;

public enum MatchType {

    REGISTER,
    VERIFY,       // 레거시 (기존 데이터 보존용, 신규 저장 안 함)
    VERIFY_ID,    // /verify/id  - 촬영 인증
    VERIFY_IMAGE, // /verify/image - 사진 인증
    IDENTIFY,
    LIVENESS
    ;
}

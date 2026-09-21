package ai.univs.gate.modules.feature.domain.enums;

/**
 * 특징점의 생애주기 사건 (UG-325).
 *
 * <p>{@link MatchType} 과 일부러 분리했다. {@code match_history} 는 인증 <b>시도</b> 로그다 — 시도마다
 * 1행이고 유사도·매칭 이미지·실패 유형이 본질이다. 등록·삭제는 <b>대상</b>의 사건이라 축이 다르다:
 * 대상 수에 비례해 적고, 유사도라는 개념이 없다. 한 열거형에 섞으면 대시보드 집계와 목록 필터가
 * 둘을 계속 구분해 내야 한다.
 *
 * <p>UPDATE 는 아직 없다. 수정은 "삭제 + 등록" 으로 대체할 예정이고, 그때 두 행을 같은
 * {@code transaction_uuid} 로 묶는다 ({@code FeatureHistory} 참고).
 */
public enum FeatureActionType {
    REGISTER,
    DELETE,
    ;
}

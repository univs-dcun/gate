package ai.univs.match.shared.swagger;

public class SwaggerDescriptions {

    /* 성공/실패 공통 DTO */
    public static final String RESPONSE_SUCCESS = "요청 성공/실패 여부";
    public static final String RESPONSE_DATA = "성공 응답 데이터";
    public static final String ERRORS = "실패 응답 데이터";
    public static final String RESPONSE_ERROR_CODE = "실패 코드";
    public static final String RESPONSE_ERROR_TYPE = "실패 타입";
    public static final String RESPONSE_ERROR_MESSAGE = "실패 메시지";
    public static final String RESPONSE_ERROR_DETAILS = "오류별 추가 정보. 없으면 생략 — MATCH-003 이면 {\"faceId\": 겹친 특징점 ID}";

    /* Match */
    public static final String BRANCH_NAME = "1 ~ 255 문자열, 특징점 브렌치 이름";
    public static final String DESCRIPTOR = "특징점";
    public static final String FACE_ID = "1 ~ 255 문자열, 사용자 고유 키";
    public static final String SIMILARITY = "소수점 유사도";
    public static final String TARGET_DESCRIPTOR = "1:1 매칭 시 매칭 대상의 특징점";
    public static final String MAX_CANDIDATES = "1 ~ 100, 1:N 후보 목록으로 받을 최대 인원 수";
}

package ai.univs.palm.shared.swagger;

public class SwaggerDescriptions {

    /* 성공/실패 공통 DTO */
    public static final String RESPONSE_SUCCESS = "요청 성공/실패 여부";
    public static final String RESPONSE_DATA = "성공 응답 데이터";
    public static final String ERRORS = "실패 응답 데이터";
    public static final String RESPONSE_ERROR_CODE = "실패 코드";
    public static final String RESPONSE_ERROR_TYPE = "실패 타입";
    public static final String RESPONSE_ERROR_MESSAGE = "실패 메시지";

    /* Face */
    public static final String PALM_ID = "1 ~ 255 문자열, 사용자 팜 고유 아이디";
    public static final String PALM_IMAGE = "사용자 팜 이미지 파일";
    public static final String TRANSACTION_UUID = "36 문자열, 팜 서비스 이력 추적 UUID";
    public static final String SIMILARITY = "매칭 유사도 점수";
    public static final String RESULT = "매칭 결과";
    public static final String CHECK_LIVENESS = "라이브니스 체크 적용 여부";
    public static final String BRANCH_NAME = "팜 관리를 위한 바운더리 이름";
    public static final String DESCRIPTOR = "팜 특징점";
    public static final String CLIENT_ID = "팜 관련 요청자 ID";
    public static final String LIVENESS_SUCCESS_BOOLEAN = "라이브니스 성공 여부 (true or false)";
    public static final String LIVENESS_SCORE = "라이브니스 점수";
    public static final String LIVENESS_THRESHOLD = "라이브니스 성공 실패 기준 점수";
    public static final String LIVENESS_MESSAGE = "라이브니스 실패 사유 메시지 (success=false 일 때만 포함)";
}
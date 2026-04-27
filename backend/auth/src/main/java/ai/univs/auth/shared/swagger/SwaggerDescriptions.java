package ai.univs.auth.shared.swagger;

public class SwaggerDescriptions {

    public static final String RESPONSE_SUCCESS = "요청 성공/실패 여부";
    public static final String RESPONSE_DATA = "성공 응답 데이터";
    public static final String ERRORS = "실패 응답 데이터";
    public static final String RESPONSE_ERROR_CODE = "실패 코드";
    public static final String RESPONSE_ERROR_TYPE = "실패 타입";
    public static final String RESPONSE_ERROR_MESSAGE = "실패 메시지";

    public static final String EXPIRES_AT = "만료 일자";
    public static final String CREATED_AT = "생성 일자";

    public static final String EMAIL = "1 ~ 255 문자열, 사용자 이메일";
    public static final String PASSWORD = "1 ~ 255 문자열, 비밀번호";
    public static final String PASSWORD_CONFIRM = "1 ~ 255 문자열, 비밀번호 확인";
    public static final String NEW_PASSWORD = "1 ~ 255 문자열, 새로운 비밀번호";
    public static final String CODE = "문자열 타입의 8자리 이메일 인증 코드";
    public static final String VERIFIED = "검증 여부";
    public static final String ACCOUNT_ID = "계정 식별 번호";
    public static final String LAST_LOGIN_AT = "마지막 로그인 일자";
    public static final String ACCESS_TOKEN = "서비스 접근 토큰";
    public static final String REFRESH_TOKEN = "서비스 접근 토큰 갱신용 리프래시 토큰";
    public static final String TOKEN_TYPE = "토큰 타입 (Bearer)";
    public static final String TOKEN_EXPIRES_IN = "만료 시간 (초)";
    public static final String VALID = "토큰 유효 여부";
}

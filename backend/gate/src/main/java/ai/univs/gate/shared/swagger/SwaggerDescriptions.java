package ai.univs.gate.shared.swagger;

public class SwaggerDescriptions {

    /* 성공/실패 공통 DTO */
    public static final String RESPONSE_SUCCESS = "요청 성공/실패 여부";
    public static final String RESPONSE_DATA = "성공 응답 데이터";
    public static final String ERRORS = "실패 응답 데이터";
    public static final String RESPONSE_ERROR_CODE = "실패 코드";
    public static final String RESPONSE_ERROR_TYPE = "실패 타입";
    public static final String RESPONSE_ERROR_MESSAGE = "실패 메시지";

    /* 페이징 */
    public static final String PAGE = "1 ~ n, 페이지 번호, 기본 값 1";
    public static final String PAGE_SIZE = "1 ~ n, 한 페이지에 보여줄 컨텐츠 수, 기본 값 10";
    public static final String TOTAL_ELEMENTS = "전체 컨텐츠 수 (검색 필터 적용)";
    public static final String TOTAL_PAGES = "전체 페이지 수";
    public static final String TOTAL_COUNT = "기준 전체 컨텐츠 수 (검색 필터 미적용)";
    public static final String SELECT_START_DATE = "검색 시작 일자";
    public static final String SELECT_END_DATE = "검색 종료 일자";

    /* 공통 */
    public static final String EXPIRES_AT = "만료 일자";
    public static final String CREATED_AT = "생성 일자";
    public static final String TRANSACTION_UUID = "요청 키";
    public static final String IS_DELETED = "삭제 여부";

    /* Project 요청/응답 DTO */
    public static final String PROJECT_NAME = "프로젝트 이름";
    public static final String PROJECT_DESCRIPTION = "프로젝트 설명";
    public static final String PROJECT_ID = "프로젝트 식별 번호";
    public static final String PROJECT_STATUS = "프로젝트 활성화 여부";
    public static final String API_KEY = "API KEY";
    public static final String UPDATED_AT = "수정 일자";
    public static final String PROJECT_KEYWORD = "프로젝트 검색 키워드";
    public static final String PROJECT_TYPE = "프로젝트 타입 [STANDARD | EXTERNAL]";
    public static final String PROJECT_MODULE_TYPE = "프로젝트 모듈 타입 [FACE | PALM] (생성 후 변경 불가)";
    public static final String PACKAGE_KEY = "패키지 키 (External 타입 프로젝트 전용, 최대 99자)";
    public static final String API_KEY_ID = "API KEY 식별 번호";
    public static final String MASKED_API_KEY = "보호 처리된 API KEY 정보";
    public static final String ISSUED_AT = "발행 일자";
    public static final String IS_ACTIVE = "활성화 여부";
    public static final String COMPANY_ID = "기업 식별 번호";
    public static final String COMPANY_NAME = "기업 이름";
    public static final String BUSINESS_NUMBER = "기업 식별자";
    public static final String MANAGER_MAIL = "담당자 메일";
    public static final String MANAGER_NAME = "담당자 이름";
    public static final String MANAGER_NUMBER = "담당자 전화번호";
    public static final String MAIN_SERVICE = "대표 서비스";
    public static final String BUSINESS_TYPE = "업태";
    public static final String EMPLOYEE_COUNT = "직원 수";
    public static final String PROJECT_SETTINGS_ID = "프로젝트 설정 식별 번호";
    public static final String CONSENT_ENABLED = "프로젝트 개인 정보 수집 동의 여부";
    public static final String CONSENT_AGREED_AT = "프로젝트 개인 정보 수집 동의 일자";
    public static final String LIVENESS_RECORDING_ENABLED = "사용자 등록시 라이브니스 적용 여부";
    public static final String LIVENESS_IDENTIFYING_ENABLED = "사용자 매칭시 라이브니스 적용 여부";
    public static final String LIVENESS_VERIFYING_ENABLED = "사용자 확인시 라이브니스 적용 여부";
    public static final String PROJECT_PLAN_TYPE = "프로젝트 플랜 타입 [FREE | ENTERPRISE]";
    public static final String PLAN_STARTED_AT = "프로젝트 플랜 시작일";
    public static final String PROJECT_PLAN_EXPIRY = "프로젝트 플랜 만료 일자";
    public static final String PLAN_REMAINING_DAYS = "프로젝트 플랜 남은 일수";
    public static final String USER_REGISTRATION_LIMIT = "유저 등록 요청 제한 수";
    public static final String COUNT_USER_REGISTRATION = "유저 등록 요청 수";
    public static final String VERIFY_LIMIT = "1:1 확인 요청 제한 수";
    public static final String COUNT_VERIFY = "1:1 확인 요청 수";
    public static final String IDENTIFY_LIMIT = "1:N 매칭 요청 제한 수";
    public static final String COUNT_IDENTIFY = "1:N 매칭 요청 수";
    public static final String LIVENESS_LIMIT = "라이브니스 요청 제한 수";
    public static final String COUNT_LIVENESS = "라이브니스 요청 수";
    public static final String PROJECT_CONFIG_CODE = "프로젝트 설정 조회 코드";
    public static final String DEMO_ENABLED = "데모 활성화 여부";
    public static final String SDK_ENABLED = "SDK 활성화 여부";
    public static final String VERIFY_ALLOCATED = "1:1 확인 할당(사용 가능) 횟수";
    public static final String USER_REGISTRATION_ALLOCATED = "사용자 등록 총 한도 (플랜 기본 + Storage Expansion 합산)";
    public static final String IDENTIFY_ALLOCATED = "1:N 매칭 할당(사용 가능) 횟수";
    public static final String LIVENESS_ALLOCATED = "라이브니스 할당(사용 가능) 횟수";

    /* Auth 요청/응답 DTO */
    public static final String JWT = "JWT 토큰";
    public static final String EMAIL = "1 ~ 255 문자열, 사용자 이메일";
    public static final String PASSWORD = "1 ~ 255 문자열, 비밀번호";
    public static final String PASSWORD_CONFIRM = "1 ~ 255 문자열, 비밀번호";
    public static final String NEW_PASSWORD = "1 ~ 255 문자열, 새로운 비밀번호";
    public static final String CODE = "문자열 타입의 6자리 숫자 이메일 인증 코드";
    public static final String VERIFIED = "검증 여부";
    public static final String ACCOUNT_ID = "계정 식별 번호";
    public static final String LAST_LOGIN_AT = "마지막 로그인 일자";
    public static final String ACCESS_TOKEN = "서비스 접근 토큰";
    public static final String REFRESH_TOKEN = "서비스 접근 토큰 갱신용 리프래시 토큰";
    public static final String TOKEN_TYPE = "토큰 타입 (Bearer)";
    public static final String PASSWORD_RESET_TOKEN = "비밀번호 재설정 토큰";
    public static final String TOKEN_EXPIRES_IN = "만료 시간 (초)";

    /* MAIL 요청/응답 DTO */
    public static final String MAIL_TITLE = "메일 제목";
    public static final String MAIL_CONTENT = "메일 내용";
    public static final String MAIL_IMAGES = "메일 이미지";

    /* User 요청/응답 DTO */
    public static final String USER_ID = "사용자 식별 번호";
    public static final String FACE_IMAGE = "사용자 얼굴 이미지";
    public static final String FACE_ID = "사용자 식별자";
    public static final String USER_DESCRIPTION = "사용자 정보";
    public static final String FACE_IMAGE_PATH = "사용자 이미지 경로";
    public static final String USER_KEYWORD = "검색용 사용자 이름 또는 성";
    
    /* Match 요청/응답 DTO */
    public static final String MATCHING_HISTORY_ID = "매칭 이력 식별 번호";
    public static final String MATCHING_TYPE = "매칭 타입 [IDENTIFY(1:N) | VERIFY(1:1) | LIVENESS]";
    public static final String MATCHING_HISTORY_TYPE = "검색용 매칭 타입 [IDENTIFY(1:N) | VERIFY(1:1) | LIVENESS | ALL]";
    public static final String MATCHING_TIME = "매칭 시간";
    public static final String CHECK_LIVENESS = "라이브니스 적용 여부";
    public static final String MATCHING_SUCCESS = "매칭 성공 여부";
    public static final String SIMILARITY = "매칭 유사도";
    public static final String MATCHING_FACE_IMAGE = "매칭 이미지";
    public static final String MATCHING_FACE_IMAGE_PATH = "매칭 이미지 파일 경로";
    public static final String MATCHING_FAILURE_TYPE = "매칭 실패 타입";
    public static final String MATCHING_FAILURE_REASON = "매칭 실패 이유";
    public static final String MATCHING_FACE_ID = "매칭 얼굴 아이디";
    public static final String TARGET_MATCHING_FACE_IMAGE = "매칭 대상 이미지 파일 (1:1 이미지 확인시 라이브니스 대상입니다.)";
    public static final String TARGET_MATCHING_FACE_IMAGE_PATH = "매칭 대상 이미지 파일 경로";
    public static final String MATCHING_RESULT_TYPE = "매칭 성공 여부";
    public static final String MATCHING_HISTORY_RESULT_TYPE = "검색용 매칭 성공 여부 [SUCCESS | FAILURE | ALL]";
    public static final String MATCHING_KEYWORD = "매칭 검색 키워드";
    public static final String CREATE_USER_CODE = "사용자 등록 코드";
    public static final String VERIFY_CODE = "사용자 확인 코드";
    public static final String IDENTIFY_CODE = "사용자 매칭 코드";
    public static final String LIVENESS_CODE = "라이브니스 코드";
    public static final String BASE64_QR_CODE = "Base64 기반 QR 코드";
    public static final String QR_LINK = "QR URL";
    public static final String LIVENESS_SUCCESS = "라이브니스 성공 여부";
    public static final String LIVENESS_FAILURE_REASON = "라이브니스 실패 사유";

    /* Dashboard 요청/응답 DTO */
    public static final String DASHBOARD_DAILY_STAT_DATE = "날짜 (UTC 기준, yyyy/MM/dd)";
    public static final String DASHBOARD_RATIO_REGISTRATION = "등록/삭제 비율";
    public static final String DASHBOARD_RATIO_VERIFY = "1:1 확인 성공/실패 비율";
    public static final String DASHBOARD_RATIO_IDENTIFY = "1:N 매칭 성공/실패 비율";
    public static final String DASHBOARD_RATIO_LIVENESS = "라이브니스 리얼/페이크 비율";
    public static final String DASHBOARD_RATIO_SUMMARY_PP = "주요(등록, 성공, Real) 비율 (0~100%)";
    public static final String DASHBOARD_RATIO_SUMMARY_SP = "보조(삭제, 실패, Fake) 비율 (0~100%)";
    public static final String DASHBOARD_RATIO_SUMMARY_PC = "주요(등록, 성공, Real) 수";
    public static final String DASHBOARD_RATIO_SUMMARY_SC = "보조(삭제, 실패, Fake) 수";
    public static final String DASHBOARD_USAGE_REGISTRATION = "등록 사용 정보";
    public static final String DASHBOARD_USAGE_VERIFY = "1:1 확인 사용 정보";
    public static final String DASHBOARD_USAGE_IDENTIFY = "1:N 매칭 사용 정보";
    public static final String DASHBOARD_USAGE_LIVENESS = "라이브니스 사용 정보";
    public static final String DASHBOARD_USAGE_TOTAL_COUNT = "총 사용 개수";
    public static final String DASHBOARD_USAGE_PERCENT = "사용률 (0~100%)";
    public static final String DASHBOARD_USAGE_REMAINING = "잔여 건수";
    public static final String DASHBOARD_USAGE_ALLOCATED = "총 한도 (플랜 배분량 또는 Storage 용량)";
    public static final String DASHBOARD_TREND_PERIOD = "조회 기간 [WEEK: 최근 7일 | MONTH: 최근 30일 | YEAR: 최근 12개월]";
    public static final String DASHBOARD_TREND_LABELS = "WEEK/MONTH: \"yyyy-MM-dd\"  YEAR: \"yyyy-MM\"";
    public static final String DASHBOARD_TREND_REGISTRATION = "등록 지표(년/월/일)";
    public static final String DASHBOARD_TREND_VERIFY = "1:1 확인 지표(년/월/일)";
    public static final String DASHBOARD_TREND_IDENTIFY = "1:N 매칭 지표(년/월/일)";
    public static final String DASHBOARD_TREND_LIVENESS = "라이브니스 지표(년/월/일)";

    /* Webhooks 요청/응답 DTO */
    public static final String WEBHOOK_CONFIG_ID = "웹훅 설정 식별 번호";
    public static final String WEBHOOK_URL = "웹훅 URL (https:// 형태)";
    public static final String WEBHOOK_DEMO_ENABLED = "웹훅 데모 활성화 여부";
    public static final String WEBHOOK_SDK_ENABLED = "웹훅 SDK 활성화 여부";
    public static final String WEBHOOK_API_ENABLED = "웹훅 API 활성화 여부";
}
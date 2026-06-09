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
    public static final String LIVENESS_REGISTER_ENABLED = "등록시 라이브니스 적용 여부";
    public static final String LIVENESS_IDENTIFYING_ENABLED = "매칭시 라이브니스 적용 여부";
    public static final String LIVENESS_VERIFYING_BY_ID_ENABLED    = "1:1 촬영 인증(/verify/id)시 라이브니스 적용 여부";
    public static final String LIVENESS_VERIFYING_BY_IMAGE_ENABLED = "1:1 사진 인증(/verify/image)시 라이브니스 적용 여부";
    public static final String LIVENESS_SETTINGS = "모듈별 라이브니스 설정 목록";
    public static final String LIVENESS_OPERATION = "라이브니스 적용 오퍼레이션 [REGISTER | IDENTIFY | VERIFY_ID | VERIFY_IMAGE]";
    public static final String LIVENESS_ENABLED = "라이브니스 활성화 여부";
    public static final String COUNT_REGISTRATION = "등록 요청 수";
    public static final String COUNT_VERIFY_BY_ID    = "1:1 촬영 인증(/verify/id) 요청 수";
    public static final String COUNT_VERIFY_BY_IMAGE = "1:1 사진 인증(/verify/image) 요청 수";
    public static final String COUNT_IDENTIFY = "1:N 매칭 요청 수";
    public static final String COUNT_LIVENESS = "라이브니스 요청 수";

    /* Auth 요청/응답 DTO */
    public static final String ACCOUNT_ID = "계정 식별 번호";

    /* Feature 요청/응답 DTO */
    public static final String FACE_IMAGE = "사용자 얼굴 이미지";
    public static final String FACE_ID = "사용자 식별자";
    public static final String FACE_IMAGE_PATH = "사용자 이미지 경로";
    public static final String USER_KEYWORD = "검색용 사용자 이름 또는 성";
    
    /* Match 요청/응답 DTO */
    public static final String MATCHING_HISTORY_ID = "매칭 이력 식별 번호";
    public static final String MATCHING_TYPE = "매칭 타입 [IDENTIFY(1:N) | VERIFY(1:1) | LIVENESS]";
    public static final String MATCHING_HISTORY_TYPE = "검색용 매칭 타입 [IDENTIFY(1:N) | VERIFY(1:1 레거시) | VERIFY_ID(1:1 촬영) | VERIFY_IMAGE(1:1 사진) | LIVENESS | ALL]";
    public static final String MATCHING_TIME = "매칭 시간";
    public static final String CHECK_LIVENESS = "라이브니스 적용 여부";
    public static final String MATCHING_SUCCESS = "매칭 성공 여부";
    public static final String SIMILARITY = "매칭 유사도";
    public static final String DESCRIPTOR = "특징점";
    public static final String TARGET_DESCRIPTOR = "매칭 대상 특징점";
    public static final String MATCHING_FACE_IMAGE = "매칭 이미지";
    public static final String MATCHING_FAILURE_TYPE = "매칭 실패 타입";
    public static final String MATCHING_FAILURE_REASON = "매칭 실패 이유";
    public static final String MATCHING_FEATURE_ID = "매칭 특징점 아이디";
    public static final String TARGET_MATCHING_FEATURE_IMAGE = "매칭 대상 이미지 파일 (1:1 이미지 확인시 라이브니스 대상입니다.)";
    public static final String MATCHING_RESULT_TYPE = "매칭 성공 여부";
    public static final String MATCHING_HISTORY_RESULT_TYPE = "검색용 매칭 성공 여부 [SUCCESS | FAILURE | ALL]";
    public static final String MATCHING_KEYWORD = "매칭 검색 키워드";
    public static final String LIVENESS_SUCCESS = "라이브니스 성공 여부";
    public static final String LIVENESS_FAILURE_REASON = "라이브니스 실패 사유";

    /* Dashboard 요청/응답 DTO */
    public static final String DASHBOARD_DAILY_STAT_DATE = "날짜 (UTC 기준, yyyy/MM/dd)";
    public static final String DASHBOARD_RATIO_REGISTRATION = "등록/삭제 비율";
    public static final String DASHBOARD_RATIO_IDENTIFY = "1:N 매칭 성공/실패 비율";
    public static final String DASHBOARD_RATIO_LIVENESS = "라이브니스 리얼/페이크 비율";
    public static final String DASHBOARD_RATIO_SUMMARY_PP = "주요(등록, 성공, Real) 비율 (0~100%)";
    public static final String DASHBOARD_RATIO_SUMMARY_SP = "보조(삭제, 실패, Fake) 비율 (0~100%)";
    public static final String DASHBOARD_RATIO_SUMMARY_PC = "주요(등록, 성공, Real) 수";
    public static final String DASHBOARD_RATIO_SUMMARY_SC = "보조(삭제, 실패, Fake) 수";
    public static final String DASHBOARD_USAGE_REGISTRATION = "등록 사용 정보";
    public static final String DASHBOARD_USAGE_IDENTIFY = "1:N 매칭 사용 정보";
    public static final String DASHBOARD_USAGE_LIVENESS = "라이브니스 사용 정보";
    public static final String DASHBOARD_USAGE_TOTAL_COUNT = "총 사용 개수";
    public static final String DASHBOARD_TREND_PERIOD = "조회 기간 [TODAY: 오늘 하루 | WEEK: 최근 7일 | MONTH: 최근 30일 | YEAR: 최근 12개월]";
    public static final String DASHBOARD_TREND_LABELS = "TODAY: \"HH\" (00~23)  WEEK/MONTH: \"yyyy-MM-dd\"  YEAR: \"yyyy-MM\"";
    public static final String DASHBOARD_TREND_REGISTRATION = "등록 지표(년/월/일)";
    public static final String DASHBOARD_TREND_IDENTIFY = "1:N 매칭 지표(년/월/일)";
    public static final String DASHBOARD_TREND_LIVENESS = "라이브니스 지표(년/월/일)";

    /* Webhooks 요청/응답 DTO */
    public static final String WEBHOOK_CONFIG_ID = "웹훅 설정 식별 번호";
    public static final String WEBHOOK_URL = "웹훅 URL (https:// 형태)";
    public static final String WEBHOOK_DEMO_ENABLED = "웹훅 데모 활성화 여부";
    public static final String WEBHOOK_API_ENABLED = "웹훅 API 활성화 여부";

    /* 공통 추가 */
    public static final String PAGE_INFO = "페이징 정보";
    public static final String REGISTERED_AT = "등록 일시";

    /* Feature 공통 */
    public static final String FEATURE_IMAGE = "특징점 이미지";
    public static final String FEATURE_AI_ID = "특징점 아이디";
    public static final String FEATURE_SEQ_ID = "특징점 식별 번호";
    public static final String FEATURE_DESCRIPTION = "특징점 설명";
    public static final String FEATURE_IMAGE_PATH = "특징점 이미지 경로";
    public static final String MATCHED_FEATURE_IMAGE_PATH = "매칭 이미지 경로";
    public static final String TARGET_FEATURE_IMAGE_PATH = "대상 이미지 경로";
    public static final String EXTERNAL_KEY = "외부 연결 키 (face ↔ palm 연결용)";
    public static final String THRESHOLD = "판정 임계값";
    public static final String LIVENESS_SCORE = "라이브니스 점수";

    /* Feature 타입 / 통합 목록 */
    public static final String FEATURE_TYPE_ALL = "특징점 타입 [FACE | PALM | ALL]";
    public static final String FEATURE_TYPE = "특징점 타입 [FACE | PALM]";
    public static final String FEATURE_FID = "FID (Face ID 또는 Palm ID)";
    public static final String FEATURE_IMAGE_URL = "이미지 URL";
    public static final String FEATURE_MEMO = "메모 (설명)";
    public static final String FEATURE_LIST_COMBINED = "특징점 목록 (Face + Palm 통합)";
    public static final String FEATURE_KEYWORD = "검색 키워드 (FID 또는 메모)";

    /* Face Feature */
    public static final String FACE_FEATURE_ID = "특징점 얼굴 식별 번호";
    public static final String FACE_FEATURE_DESCRIPTION = "특징점 얼굴 설명";
    public static final String FACE_FEATURE_LIST = "특징점 얼굴 목록";

    /* Palm Feature */
    public static final String PALM_FEATURE_ID = "팜 식별 번호";
    public static final String PALM_IMAGE = "팜 이미지";
    public static final String PALM_IMAGE_OPTIONAL = "새 팜 이미지 (선택)";
    public static final String PALM_DESCRIPTION = "팜 설명";
    public static final String PALM_FEATURE_AI_ID = "팜 AI 서비스 식별자";
    public static final String PALM_FEATURE_LIST = "특징점 팜 목록";
    public static final String PALM_KEYWORD = "검색 키워드 (featureId, description, transactionUuid)";

    /* Dashboard 추가 */
    public static final String DASHBOARD_FEATURE_TYPE = "특징점 타입 (FACE | PALM)";
    public static final String DASHBOARD_USAGE_VERIFY_BY_ID = "1:1 촬영 인증 건수 (/verify/id)";
    public static final String DASHBOARD_USAGE_VERIFY_BY_IMAGE = "1:1 사진 인증 건수 (/verify/image)";
    public static final String DASHBOARD_USAGE_PERIOD_COUNT = "선택한 기간 내 사용 건수";
    public static final String DASHBOARD_RATIO_VERIFY_BY_ID = "1:1 촬영 인증 성공/실패 비율 (/verify/id)";
    public static final String DASHBOARD_RATIO_VERIFY_BY_IMAGE = "1:1 사진 인증 성공/실패 비율 (/verify/image)";
    public static final String DASHBOARD_TREND_VERIFY_BY_ID = "1:1 촬영 인증 추이 (/verify/id)";
    public static final String DASHBOARD_TREND_VERIFY_BY_IMAGE = "1:1 사진 인증 추이 (/verify/image)";
    public static final String DASHBOARD_DAILY_STATS_LIST = "일일 통계 목록";

    /* Match History 추가 */
    public static final String MATCHING_HISTORY_LIST = "매칭 이력 목록";
    public static final String MATCHED_FEATURE_ID = "매칭 대상 특징점 아이디";

    /* Project 추가 */
    public static final String PROJECT_LIST = "프로젝트 목록";

    /* Consent Log */
    public static final String CONSENT_LOG_ID = "동의 이력 식별 번호";
    public static final String CONSENT_LOG_ACCOUNT_ID = "변경 계정 식별 번호";
    public static final String CONSENT_LOG_TYPE = "동의 유형";
    public static final String CONSENT_LOG_AGREED = "동의 여부";
    public static final String CONSENT_LOG_IP = "요청 IP";
    public static final String CONSENT_LOG_AGREED_AT = "동의 일자";
}
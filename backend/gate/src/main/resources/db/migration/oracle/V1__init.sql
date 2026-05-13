-- api_keys
CREATE TABLE api_keys (
    api_key_id  NUMBER(20,0)    GENERATED ALWAYS AS IDENTITY NOT NULL,
    project_id  NUMBER(20,0)    NOT NULL,
    api_key     VARCHAR2(255)   NOT NULL,
    secret_key  VARCHAR2(255)   NOT NULL,
    issued_at   TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at  TIMESTAMP       NOT NULL,
    is_active   NUMBER(1,0)     DEFAULT 0 NOT NULL,
    created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (api_key_id),
    UNIQUE (api_key)
);

COMMENT ON TABLE  api_keys              IS 'API 키';
COMMENT ON COLUMN api_keys.api_key_id  IS 'API KEY 식별 번호';
COMMENT ON COLUMN api_keys.project_id  IS '프로젝트 식별 번호';
COMMENT ON COLUMN api_keys.api_key     IS 'API KEY';
COMMENT ON COLUMN api_keys.secret_key  IS 'API Secret Key';
COMMENT ON COLUMN api_keys.issued_at   IS 'API KEY 발행 일자';
COMMENT ON COLUMN api_keys.expires_at  IS 'API KEY 만료 일자';
COMMENT ON COLUMN api_keys.is_active   IS 'API KEY 활성화 여부';
COMMENT ON COLUMN api_keys.created_at  IS 'API KEY 생성 일자';
COMMENT ON COLUMN api_keys.updated_at  IS 'API KEY 변경 일자';


-- companies
CREATE TABLE companies (
    company_id          NUMBER(20,0)    GENERATED ALWAYS AS IDENTITY NOT NULL,
    account_id          NUMBER(20,0)    NOT NULL,
    company_name        VARCHAR2(100)   DEFAULT '',
    business_number     VARCHAR2(20)    DEFAULT '',
    manager_mail        VARCHAR2(255)   DEFAULT '',
    manager_name        VARCHAR2(100)   DEFAULT '',
    manager_number      VARCHAR2(100)   DEFAULT '',
    main_service        VARCHAR2(100)   DEFAULT '',
    business_type       VARCHAR2(100)   DEFAULT '',
    employee_count      VARCHAR2(100)   DEFAULT '',
    created_by          NUMBER(20,0)    NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    updated_by          NUMBER(20,0)    NOT NULL,
    updated_at          TIMESTAMP       NOT NULL,
    PRIMARY KEY (company_id)
);

COMMENT ON TABLE  companies                     IS '기업';
COMMENT ON COLUMN companies.company_id          IS '기업 식별 번호';
COMMENT ON COLUMN companies.account_id          IS '계정 식별 번호';
COMMENT ON COLUMN companies.company_name        IS '회사 이름';
COMMENT ON COLUMN companies.business_number     IS '기업 고유 식별자';
COMMENT ON COLUMN companies.manager_mail        IS '담당자 메일';
COMMENT ON COLUMN companies.manager_name        IS '담당자 이름';
COMMENT ON COLUMN companies.manager_number      IS '담당자 전화번호';
COMMENT ON COLUMN companies.main_service        IS '메인 서비스';
COMMENT ON COLUMN companies.business_type       IS '업태';
COMMENT ON COLUMN companies.employee_count      IS '직원수';
COMMENT ON COLUMN companies.created_by          IS '기업 생성자';
COMMENT ON COLUMN companies.created_at          IS '기업 생성 일자';
COMMENT ON COLUMN companies.updated_by          IS '기업 정보 변경자';
COMMENT ON COLUMN companies.updated_at          IS '기업 정보 변경 일자';


-- consent_logs
CREATE TABLE consent_logs (
    consent_log_id          NUMBER(20,0)    GENERATED ALWAYS AS IDENTITY NOT NULL,
    project_id              NUMBER(20,0)    NOT NULL,
    end_user_identifier     NUMBER(20,0)    NOT NULL,
    consent_type            VARCHAR2(20)    DEFAULT 'PRIVACY' NOT NULL,
    agreed                  NUMBER(1,0)     DEFAULT 0 NOT NULL,
    ip_address              VARCHAR2(255)   DEFAULT NULL,
    agreed_at               TIMESTAMP       DEFAULT NULL,
    created_at              TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (consent_log_id)
);

COMMENT ON TABLE  consent_logs                          IS '이용 약관 이력';
COMMENT ON COLUMN consent_logs.consent_log_id           IS '이용 약관 이력 식별 번호';
COMMENT ON COLUMN consent_logs.project_id               IS '프로젝트 식별 번호';
COMMENT ON COLUMN consent_logs.end_user_identifier      IS '이용 약관 변경자';
COMMENT ON COLUMN consent_logs.consent_type             IS '이용 약관 여부 타입';
COMMENT ON COLUMN consent_logs.agreed                   IS '이용 약관 동의 여부';
COMMENT ON COLUMN consent_logs.ip_address               IS '요청 IP';
COMMENT ON COLUMN consent_logs.agreed_at                IS '이용 약관 동의 일자';
COMMENT ON COLUMN consent_logs.created_at               IS '이용 약관 생성 일자';


-- match_history
CREATE TABLE match_history (
    match_history_id        NUMBER(20,0)    GENERATED ALWAYS AS IDENTITY NOT NULL,
    project_id              NUMBER(20,0)    NOT NULL,
    match_type              VARCHAR2(20)    NOT NULL,
    match_time              TIMESTAMP       NOT NULL,
    check_liveness          NUMBER(1,0)     NOT NULL,
    success                 NUMBER(1,0)     NOT NULL,
    user_id                 NUMBER(20,0)    DEFAULT NULL,
    face_id                 VARCHAR2(100)   DEFAULT '',
    user_description        VARCHAR2(1000)  DEFAULT '',
    similarity              NUMBER(5,2)     DEFAULT 0,
    face_image_path         VARCHAR2(100)   DEFAULT '',
    match_face_image_path   VARCHAR2(100)   DEFAULT '',
    match_face_id           VARCHAR2(100)   DEFAULT NULL,
    failure_type            VARCHAR2(100)   DEFAULT '',
    failure_reason          VARCHAR2(1000)  DEFAULT '',
    transaction_uuid        VARCHAR2(36)    NOT NULL,
    created_by              NUMBER(20,0)    NOT NULL,
    created_at              TIMESTAMP       NOT NULL,
    updated_by              NUMBER(20,0)    NOT NULL,
    updated_at              TIMESTAMP       NOT NULL,
    PRIMARY KEY (match_history_id)
);

COMMENT ON TABLE  match_history                         IS '매칭 이력';
COMMENT ON COLUMN match_history.match_history_id        IS '매치 식별 번호';
COMMENT ON COLUMN match_history.project_id              IS '프로젝트 식별 번호';
COMMENT ON COLUMN match_history.match_type              IS '매치 타입';
COMMENT ON COLUMN match_history.match_time              IS '매치 시간';
COMMENT ON COLUMN match_history.check_liveness          IS '라이브니스 적용 여부';
COMMENT ON COLUMN match_history.success                 IS '매치 성공 여부';
COMMENT ON COLUMN match_history.user_id                 IS '매치 성공시 사용자 식별 번호';
COMMENT ON COLUMN match_history.face_id                 IS '얼굴 식별자';
COMMENT ON COLUMN match_history.user_description        IS '사용자 정보';
COMMENT ON COLUMN match_history.similarity              IS '매치 유사도 점수';
COMMENT ON COLUMN match_history.face_image_path         IS '원본 이미지 경로';
COMMENT ON COLUMN match_history.match_face_image_path   IS '매치 이미지 경로';
COMMENT ON COLUMN match_history.match_face_id           IS '1:1 매치에서 매치 대상 얼굴 아이디';
COMMENT ON COLUMN match_history.failure_type            IS '실패 타입';
COMMENT ON COLUMN match_history.failure_reason          IS '실패 사유';
COMMENT ON COLUMN match_history.transaction_uuid        IS '트랜잭션 키';
COMMENT ON COLUMN match_history.created_by              IS '매치 이력 생성자';
COMMENT ON COLUMN match_history.created_at              IS '매치 이력 생성 일자';
COMMENT ON COLUMN match_history.updated_by              IS '매치 이력 변경자';
COMMENT ON COLUMN match_history.updated_at              IS '매치 이력 변경 일자';


-- project_settings
CREATE TABLE project_settings (
    setting_id                      NUMBER(20,0)    GENERATED ALWAYS AS IDENTITY NOT NULL,
    project_id                      NUMBER(20,0)    NOT NULL,
    demo_enabled                    NUMBER(1,0)     DEFAULT 1 NOT NULL,
    consent_enabled                 NUMBER(1,0)     DEFAULT 0 NOT NULL,
    consent_agreed_at               TIMESTAMP       DEFAULT NULL,
    liveness_recording_enabled      NUMBER(1,0)     DEFAULT 0 NOT NULL,
    liveness_identifying_enabled    NUMBER(1,0)     DEFAULT 0 NOT NULL,
    liveness_verifying_enabled      NUMBER(1,0)     DEFAULT 0 NOT NULL,
    created_at                      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (setting_id)
);

COMMENT ON TABLE  project_settings                              IS '프로젝트 설정';
COMMENT ON COLUMN project_settings.setting_id                   IS '프로젝트 설정 식별 번호';
COMMENT ON COLUMN project_settings.project_id                   IS '프로젝트 식별 번호';
COMMENT ON COLUMN project_settings.demo_enabled                 IS '데모 활성화 여부';
COMMENT ON COLUMN project_settings.consent_enabled              IS '이용 약관 여부';
COMMENT ON COLUMN project_settings.consent_agreed_at            IS '이용 약관 동의 일자';
COMMENT ON COLUMN project_settings.liveness_recording_enabled   IS '얼굴 등록 라이브니스 적용 여부';
COMMENT ON COLUMN project_settings.liveness_identifying_enabled IS '얼굴 1:N 매칭 라이브니스 적용 여부';
COMMENT ON COLUMN project_settings.liveness_verifying_enabled   IS '얼굴 1:1 매칭 라이브니스 적용 여부';
COMMENT ON COLUMN project_settings.created_at                   IS '프로젝트 설정 생성 일자';
COMMENT ON COLUMN project_settings.updated_at                   IS '프로젝트 설정 변경 일자';


-- projects
CREATE TABLE projects (
    project_id              NUMBER(20,0)    GENERATED ALWAYS AS IDENTITY NOT NULL,
    account_id              NUMBER(20,0)    NOT NULL,
    project_name            VARCHAR2(255)   NOT NULL,
    project_description     VARCHAR2(1000)  DEFAULT '',
    status                  VARCHAR2(20)    DEFAULT 'ACTIVE',
    project_type            VARCHAR2(20)    DEFAULT 'STANDARD' NOT NULL,
    project_module_type     VARCHAR2(20)    DEFAULT 'FACE' NOT NULL,
    package_key             VARCHAR2(99)    DEFAULT NULL,
    is_deleted              NUMBER(1,0)     DEFAULT 0 NOT NULL,
    branch_name             VARCHAR2(36)    NOT NULL,
    created_by              NUMBER(20,0)    NOT NULL,
    created_at              TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by              NUMBER(20,0)    NOT NULL,
    updated_at              TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (project_id)
);

COMMENT ON TABLE  projects                      IS '프로젝트';
COMMENT ON COLUMN projects.project_id           IS '프로젝트 식별 번호';
COMMENT ON COLUMN projects.account_id           IS '계정 식별 번호';
COMMENT ON COLUMN projects.project_name         IS '프로젝트 이름';
COMMENT ON COLUMN projects.project_description  IS '프로젝트 설명';
COMMENT ON COLUMN projects.status               IS '프로젝트 상태';
COMMENT ON COLUMN projects.project_type         IS '프로젝트 타입';
COMMENT ON COLUMN projects.project_module_type  IS '프로젝트 모듈 타입';
COMMENT ON COLUMN projects.package_key          IS '외부 연동 모듈 키';
COMMENT ON COLUMN projects.is_deleted           IS '프로젝트 삭제 여부';
COMMENT ON COLUMN projects.branch_name          IS '특징점 바운더리 키';
COMMENT ON COLUMN projects.created_by           IS '프로젝트 생성자';
COMMENT ON COLUMN projects.created_at           IS '프로젝트 생성 일자';
COMMENT ON COLUMN projects.updated_by           IS '프로젝트 변경자';
COMMENT ON COLUMN projects.updated_at           IS '프로젝트 변경 일자';


-- sdk_qr_codes
CREATE TABLE sdk_qr_codes (
    code        VARCHAR2(36)    NOT NULL,
    token       CLOB            NOT NULL,
    type        VARCHAR2(20)    NOT NULL,
    expires_at  TIMESTAMP       NOT NULL,
    is_used     NUMBER(1,0)     DEFAULT 0 NOT NULL,
    PRIMARY KEY (code)
);

COMMENT ON TABLE  sdk_qr_codes              IS '데모 QR 코드';
COMMENT ON COLUMN sdk_qr_codes.code         IS 'QR 코드 UUID';
COMMENT ON COLUMN sdk_qr_codes.token        IS 'QR 토큰 (JWT)';
COMMENT ON COLUMN sdk_qr_codes.type         IS 'QR 코드 유형 (CREATE_USER, VERIFY, IDENTIFY, LIVENESS)';
COMMENT ON COLUMN sdk_qr_codes.expires_at   IS '만료 일시 (UTC)';
COMMENT ON COLUMN sdk_qr_codes.is_used      IS '사용 여부';


-- users
CREATE TABLE users (
    user_id             NUMBER(20,0)    GENERATED ALWAYS AS IDENTITY NOT NULL,
    project_id          NUMBER(20,0)    NOT NULL,
    face_id             VARCHAR2(100)   NOT NULL,
    face_image_path     VARCHAR2(255)   DEFAULT '',
    description         VARCHAR2(255)   DEFAULT '',
    username            VARCHAR2(255)   DEFAULT '',
    is_deleted          NUMBER(1,0)     DEFAULT 0 NOT NULL,
    transaction_uuid    VARCHAR2(36)    NOT NULL,
    created_by          NUMBER(20,0)    NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    updated_by          NUMBER(20,0)    NOT NULL,
    updated_at          TIMESTAMP       NOT NULL,
    PRIMARY KEY (user_id)
);

COMMENT ON TABLE  users                     IS '사용자';
COMMENT ON COLUMN users.user_id             IS '사용자 식별 번호';
COMMENT ON COLUMN users.project_id          IS '프로젝트 식별 번호';
COMMENT ON COLUMN users.face_id             IS '사용자 페이스아이디';
COMMENT ON COLUMN users.face_image_path     IS '사용자 얼굴 이미지 경로';
COMMENT ON COLUMN users.description         IS '사용자 정보';
COMMENT ON COLUMN users.username            IS '사용자 이름';
COMMENT ON COLUMN users.is_deleted          IS '사용자 삭제 여부';
COMMENT ON COLUMN users.transaction_uuid    IS '요청 키';
COMMENT ON COLUMN users.created_by          IS '사용자 정보 생성자';
COMMENT ON COLUMN users.created_at          IS '사용자 정보 생성 일자';
COMMENT ON COLUMN users.updated_by          IS '사용자 정보 변경자';
COMMENT ON COLUMN users.updated_at          IS '사용자 정보 변경 일자';


-- webhook_configs
CREATE TABLE webhook_configs (
    webhook_config_id   NUMBER(20,0)    GENERATED ALWAYS AS IDENTITY NOT NULL,
    project_id          NUMBER(20,0)    NOT NULL,
    webhook_url         VARCHAR2(500)   NOT NULL,
    demo_enabled        NUMBER(1,0)     DEFAULT 0 NOT NULL,
    sdk_enabled         NUMBER(1,0)     DEFAULT 0 NOT NULL,
    api_enabled         NUMBER(1,0)     DEFAULT 0 NOT NULL,
    created_by          NUMBER(20,0)    NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    updated_by          NUMBER(20,0)    NOT NULL,
    updated_at          TIMESTAMP       NOT NULL,
    PRIMARY KEY (webhook_config_id)
);

COMMENT ON TABLE  webhook_configs                   IS '웹훅';
COMMENT ON COLUMN webhook_configs.webhook_config_id IS '웹훅 식별 번호';
COMMENT ON COLUMN webhook_configs.project_id        IS '프로젝트 식별 번호';
COMMENT ON COLUMN webhook_configs.webhook_url        IS '웹훅 URL';
COMMENT ON COLUMN webhook_configs.demo_enabled       IS '데모 웹훅 활성화 여부';
COMMENT ON COLUMN webhook_configs.sdk_enabled        IS 'SDK 웹훅 활성화 여부';
COMMENT ON COLUMN webhook_configs.api_enabled        IS 'API 웹훅 활성화 여부';
COMMENT ON COLUMN webhook_configs.created_by         IS '웹훅 생성자';
COMMENT ON COLUMN webhook_configs.created_at         IS '웹훅 생성 일자';
COMMENT ON COLUMN webhook_configs.updated_by         IS '웹훅 변경자';
COMMENT ON COLUMN webhook_configs.updated_at         IS '웹훅 변경 일자';

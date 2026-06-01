-- accounts
CREATE TABLE accounts (
    account_id              BIGSERIAL       NOT NULL,
    email                   VARCHAR(255)    NOT NULL,
    password                VARCHAR(255)    NOT NULL,
    status                  VARCHAR(20)     DEFAULT 'ACTIVE',
    failed_login_attempts   INTEGER         DEFAULT 0,
    locked_until            TIMESTAMP       DEFAULT NULL,
    last_login_at           TIMESTAMP       DEFAULT NULL,
    last_login_ip           VARCHAR(45)     DEFAULT NULL,
    password_changed_at     TIMESTAMP       DEFAULT NULL,
    created_at              TIMESTAMP       NOT NULL,
    updated_at              TIMESTAMP       NOT NULL,
    PRIMARY KEY (account_id)
);

COMMENT ON TABLE  accounts                        IS '계정';
COMMENT ON COLUMN accounts.account_id             IS '계정 식별 번호';
COMMENT ON COLUMN accounts.email                  IS '이메일';
COMMENT ON COLUMN accounts.password               IS '비밀번호';
COMMENT ON COLUMN accounts.status                 IS '계정 상태';
COMMENT ON COLUMN accounts.failed_login_attempts  IS '로그인 실패 횟수';
COMMENT ON COLUMN accounts.locked_until           IS '계정 잠금 일자';
COMMENT ON COLUMN accounts.last_login_at          IS '마지막 로그인 일자';
COMMENT ON COLUMN accounts.last_login_ip          IS '마지막 로그인 요청 IP 주소';
COMMENT ON COLUMN accounts.password_changed_at    IS '비밀번호 변경 일시';
COMMENT ON COLUMN accounts.created_at             IS '계정 생성 일자';
COMMENT ON COLUMN accounts.updated_at             IS '계정 변경 일자';


-- email_verifications
CREATE TABLE email_verifications (
    verification_id     BIGSERIAL       NOT NULL,
    type                VARCHAR(30)     NOT NULL,
    email               VARCHAR(255)    NOT NULL,
    verification_code   VARCHAR(255)    NOT NULL,
    created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    expires_at          TIMESTAMP       NOT NULL,
    verified            BOOLEAN         DEFAULT FALSE,
    verified_at         TIMESTAMP       DEFAULT NULL,
    attempts            INTEGER         DEFAULT 0,
    PRIMARY KEY (verification_id)
);

COMMENT ON TABLE  email_verifications                   IS '이메일 인증';
COMMENT ON COLUMN email_verifications.verification_id   IS '메일 인증 식별 번호';
COMMENT ON COLUMN email_verifications.type              IS '메일 인증 타입 (SIGNUP, PASSWORD_RESET)';
COMMENT ON COLUMN email_verifications.email             IS '계정용 이메일';
COMMENT ON COLUMN email_verifications.verification_code IS '메일 인증 코드';
COMMENT ON COLUMN email_verifications.created_at        IS '메일 인증 코드 생성 일자';
COMMENT ON COLUMN email_verifications.expires_at        IS '메일 인증 코드 만료 일자';
COMMENT ON COLUMN email_verifications.verified          IS '메일 인증 여부';
COMMENT ON COLUMN email_verifications.verified_at       IS '메일 인증 완료 일자';
COMMENT ON COLUMN email_verifications.attempts          IS '메일 인증 시도 횟수';


-- login_logs
CREATE TABLE login_logs (
    log_id          BIGSERIAL       NOT NULL,
    account_id      BIGINT          DEFAULT NULL,
    login_status    VARCHAR(50)     NOT NULL,
    attempted_email VARCHAR(255)    DEFAULT NULL,
    login_at        TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    ip_address      VARCHAR(45)     DEFAULT NULL,
    user_agent      TEXT            DEFAULT NULL,
    PRIMARY KEY (log_id)
);

COMMENT ON TABLE  login_logs                 IS '로그인 이력';
COMMENT ON COLUMN login_logs.log_id          IS '로그인 이력 식별 번호';
COMMENT ON COLUMN login_logs.account_id      IS '계정 식별 번호';
COMMENT ON COLUMN login_logs.login_status    IS '로그인 상태';
COMMENT ON COLUMN login_logs.attempted_email IS '로그인을 시도한 메일';
COMMENT ON COLUMN login_logs.login_at        IS '로그인 일자';
COMMENT ON COLUMN login_logs.ip_address      IS '로그인 요청 IP 주소';
COMMENT ON COLUMN login_logs.user_agent      IS '클라이언트 정보';


-- password_histories
CREATE TABLE password_histories (
    history_id              BIGSERIAL       NOT NULL,
    account_id              BIGINT          NOT NULL,
    password_hash           VARCHAR(255)    NOT NULL,
    changed_at              TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    password_reset_method   VARCHAR(30)     NOT NULL,
    ip_address              VARCHAR(45)     DEFAULT NULL,
    user_agent              TEXT            DEFAULT NULL,
    PRIMARY KEY (history_id)
);

COMMENT ON TABLE  password_histories                        IS '비밀번호 변경 이력';
COMMENT ON COLUMN password_histories.history_id             IS '비밀번호 변경 이력 식별 번호';
COMMENT ON COLUMN password_histories.account_id             IS '계정 식별 번호';
COMMENT ON COLUMN password_histories.password_hash          IS '해시 비밀번호';
COMMENT ON COLUMN password_histories.changed_at             IS '변경 일자';
COMMENT ON COLUMN password_histories.password_reset_method  IS '변경 방법';
COMMENT ON COLUMN password_histories.ip_address             IS '요청 IP';
COMMENT ON COLUMN password_histories.user_agent             IS '클라이언트 정보';


-- refresh_tokens
CREATE TABLE refresh_tokens (
    token_id    BIGSERIAL       NOT NULL,
    account_id  BIGINT          NOT NULL,
    jti         VARCHAR(255)    NOT NULL,
    token_hash  VARCHAR(255)    DEFAULT NULL,
    issued_at   TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    expires_at  TIMESTAMP       NOT NULL,
    revoked_at  TIMESTAMP       DEFAULT NULL,
    is_revoked  BOOLEAN         DEFAULT FALSE,
    ip_address  VARCHAR(45)     DEFAULT NULL,
    user_agent  TEXT            DEFAULT NULL,
    PRIMARY KEY (token_id),
    UNIQUE (jti)
);

COMMENT ON TABLE  refresh_tokens            IS '리프래시 토큰';
COMMENT ON COLUMN refresh_tokens.token_id   IS '리프래시 토큰 식별 번호';
COMMENT ON COLUMN refresh_tokens.account_id IS '계정 식별 번호';
COMMENT ON COLUMN refresh_tokens.jti        IS 'JWT jti 검증용';
COMMENT ON COLUMN refresh_tokens.token_hash IS '리프래시 토큰';
COMMENT ON COLUMN refresh_tokens.issued_at  IS '리프래시 토큰 발행 일자';
COMMENT ON COLUMN refresh_tokens.expires_at IS '리프래시 토큰 만료 일자';
COMMENT ON COLUMN refresh_tokens.revoked_at IS '리프래시 토큰 무효화 일자';
COMMENT ON COLUMN refresh_tokens.is_revoked IS '리프래시 토큰 무효화 여부';
COMMENT ON COLUMN refresh_tokens.ip_address IS '리프래시 토큰 요청 IP 주소';
COMMENT ON COLUMN refresh_tokens.user_agent IS '클라이언트 정보';

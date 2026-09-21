-- UG-325: 특징점 생애주기 이력. 등록·삭제는 인증 시도(match_history)가 아니라 대상의 사건이다.
--
-- FK 대신 스냅샷(feature_seq·feature_id·메모·이미지·external_key)을 두는 이유: biometric_feature 가
-- 나중에 하드 삭제되거나 보존기간으로 정리돼도 "무엇이 지워졌나" 는 남아야 한다.
--
-- DEFAULT '' 를 쓰지 않는다 — 오라클은 '' 를 NULL 로 취급해 두 방언의 값이 갈린다 (UG-297).
-- 문자열 컬럼은 전부 NULL 허용, 기본값 없음.
CREATE TABLE feature_history (
    feature_history_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id           BIGINT         NOT NULL,
    feature_type         VARCHAR(10)    NOT NULL,
    action_type          VARCHAR(20)    NOT NULL,
    success              BOOLEAN        DEFAULT FALSE NOT NULL,
    check_liveness       BOOLEAN        DEFAULT FALSE NOT NULL,
    failure_type         VARCHAR(100),
    transaction_uuid     VARCHAR(36)    NOT NULL,
    feature_seq          BIGINT,
    feature_id           VARCHAR(255),
    user_description     VARCHAR(1000),
    feature_image_path   VARCHAR(255),
    consent_snapshot     BOOLEAN,
    external_key         VARCHAR(255),
    created_by           BIGINT         NOT NULL,
    created_at           TIMESTAMP      NOT NULL,
    updated_by           BIGINT         NOT NULL,
    updated_at           TIMESTAMP      NOT NULL,
    CONSTRAINT fk_feature_history_project FOREIGN KEY (project_id) REFERENCES projects(project_id)
);

-- 대시보드: 프로젝트·인증방식·사건·시각으로 센다 (where 절 순서와 같다). 통합 목록(UG-326): 프로젝트·일련번호로 한 특징점의 생애를 모은다.
CREATE INDEX idx_feature_history_project_type_action_created ON feature_history(project_id, feature_type, action_type, created_at);
CREATE INDEX idx_feature_history_project_feature_seq    ON feature_history(project_id, feature_seq);
CREATE INDEX idx_feature_history_transaction_uuid       ON feature_history(transaction_uuid);

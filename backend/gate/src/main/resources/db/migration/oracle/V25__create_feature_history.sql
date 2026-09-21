-- UG-325: 특징점 생애주기 이력 (postgresql 쌍둥이 파일의 주석 참고).
-- BOOLEAN 은 NUMBER(1), BIGINT 는 NUMBER(20,0). DEFAULT 절이 NOT NULL 앞에 온다 (오라클 문법).
-- DEFAULT '' 를 쓰지 않는다 — 오라클은 '' 를 NULL 로 취급한다 (UG-297).
CREATE TABLE feature_history (
    feature_history_id   NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id           NUMBER(20,0)   NOT NULL,
    feature_type         VARCHAR2(10)   NOT NULL,
    action_type          VARCHAR2(20)   NOT NULL,
    success              NUMBER(1,0)    DEFAULT 0 NOT NULL,
    check_liveness       NUMBER(1,0)    DEFAULT 0 NOT NULL,
    failure_type         VARCHAR2(100),
    transaction_uuid     VARCHAR2(36)   NOT NULL,
    feature_seq          NUMBER(20,0),
    feature_id           VARCHAR2(255),
    user_description     VARCHAR2(1000),
    feature_image_path   VARCHAR2(255),
    consent_snapshot     NUMBER(1,0),
    external_key         VARCHAR2(255),
    created_by           NUMBER(20,0)   NOT NULL,
    created_at           TIMESTAMP      NOT NULL,
    updated_by           NUMBER(20,0)   NOT NULL,
    updated_at           TIMESTAMP      NOT NULL,
    CONSTRAINT fk_feature_history_project FOREIGN KEY (project_id) REFERENCES projects(project_id)
);

CREATE INDEX idx_feature_history_project_action_created ON feature_history(project_id, action_type, created_at);
CREATE INDEX idx_feature_history_project_feature_seq    ON feature_history(project_id, feature_seq);
CREATE INDEX idx_feature_history_transaction_uuid       ON feature_history(transaction_uuid);

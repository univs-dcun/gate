-- biometric_feature 통합 테이블 생성
CREATE TABLE biometric_feature (
    biometric_feature_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id           NUMBER        NOT NULL,
    type                 VARCHAR2(10)  NOT NULL,
    feature_id           VARCHAR2(255),
    feature_image_path   VARCHAR2(255),
    description          CLOB,
    is_deleted           NUMBER(1)     NOT NULL DEFAULT 0,
    transaction_uuid     VARCHAR2(36),
    external_key         VARCHAR2(255),
    created_at           TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at           TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    created_by           VARCHAR2(255),
    updated_by           VARCHAR2(255),
    CONSTRAINT fk_biometric_feature_project FOREIGN KEY (project_id) REFERENCES projects(project_id)
);

CREATE INDEX idx_biometric_feature_project_type ON biometric_feature(project_id, type);
CREATE INDEX idx_biometric_feature_feature_id ON biometric_feature(feature_id);

-- face_feature 데이터 이전
INSERT INTO biometric_feature (
    project_id, type, feature_id, feature_image_path,
    description, is_deleted, transaction_uuid, external_key,
    created_at, updated_at, created_by, updated_by
)
SELECT
    project_id, 'FACE', feature_id, feature_image_path,
    description, is_deleted, transaction_uuid, external_key,
    created_at, updated_at, created_by, updated_by
FROM face_feature;

-- palm_feature 데이터 이전
INSERT INTO biometric_feature (
    project_id, type, feature_id, feature_image_path,
    description, is_deleted, transaction_uuid, external_key,
    created_at, updated_at, created_by, updated_by
)
SELECT
    project_id, 'PALM', feature_id, feature_image_path,
    description, is_deleted, transaction_uuid, external_key,
    created_at, updated_at, created_by, updated_by
FROM palm_feature;

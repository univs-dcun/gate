-- biometric_feature 통합 테이블 생성
CREATE TABLE biometric_feature (
    biometric_feature_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id           BIGINT       NOT NULL,
    type                 VARCHAR(10)  NOT NULL,
    feature_id           VARCHAR(255),
    feature_image_path   VARCHAR(255),
    description          TEXT,
    is_deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    transaction_uuid     VARCHAR(36),
    external_key         VARCHAR(255),
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255),
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

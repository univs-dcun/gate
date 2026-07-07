-- match_history: face_feature_id (BIGINT FK) 제거 — AI 서비스 ID로 대체
ALTER TABLE match_history DROP COLUMN face_feature_id;

-- match_history: AI 서비스 식별자·이미지 경로 컬럼 rename (FACE/PALM 공용)
ALTER TABLE match_history RENAME COLUMN face_id TO feature_id;
ALTER TABLE match_history RENAME COLUMN face_image_path TO feature_image_path;
ALTER TABLE match_history RENAME COLUMN match_face_id TO matched_feature_id;
ALTER TABLE match_history RENAME COLUMN match_face_image_path TO matched_feature_image_path;

-- palm_feature 테이블 생성
CREATE TABLE palm_feature (
    palm_feature_id   NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id      NUMBER          NOT NULL,
    feature_id         VARCHAR2(255),
    feature_image_path VARCHAR2(255),
    description     CLOB,
    username        VARCHAR2(255),
    is_deleted      NUMBER(1)       NOT NULL DEFAULT 0,
    transaction_uuid VARCHAR2(36),
    external_key    VARCHAR2(255),
    created_at      TIMESTAMP       NOT NULL,
    updated_at      TIMESTAMP       NOT NULL
);

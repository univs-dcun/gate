-- match_history: face_media_id → media_id (FACE/PALM 공용)
ALTER TABLE match_history RENAME COLUMN face_media_id TO media_id;

-- palm_media 테이블 생성
CREATE TABLE palm_media (
    palm_media_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id     BIGINT          NOT NULL,
    palm_id        VARCHAR(255),
    palm_image_path VARCHAR(255),
    description    TEXT,
    username       VARCHAR(255),
    is_deleted     BOOLEAN         NOT NULL DEFAULT FALSE,
    transaction_uuid VARCHAR(36),
    external_key   VARCHAR(255),
    created_at     TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP       NOT NULL DEFAULT NOW()
);

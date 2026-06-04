-- match_history: face_media_id → media_id (FACE/PALM 공용)
ALTER TABLE match_history RENAME COLUMN face_media_id TO media_id;

-- palm_media 테이블 생성
CREATE TABLE palm_media (
    palm_media_id   NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id      NUMBER          NOT NULL,
    palm_id         VARCHAR2(255),
    palm_image_path VARCHAR2(255),
    description     CLOB,
    username        VARCHAR2(255),
    is_deleted      NUMBER(1)       NOT NULL DEFAULT 0,
    transaction_uuid VARCHAR2(36),
    external_key    VARCHAR2(255),
    created_at      TIMESTAMP       NOT NULL,
    updated_at      TIMESTAMP       NOT NULL
);

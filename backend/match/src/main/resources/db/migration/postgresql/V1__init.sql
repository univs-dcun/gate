-- branch
CREATE TABLE branch (
    branch_id   BIGSERIAL       NOT NULL,
    branch_name VARCHAR(255)    NOT NULL,
    created_by  VARCHAR(36)     NOT NULL,
    created_at  TIMESTAMP       NOT NULL,
    modified_by VARCHAR(36)     NOT NULL,
    modified_at TIMESTAMP       NOT NULL,
    PRIMARY KEY (branch_id)
);

COMMENT ON TABLE  branch              IS '브랜치 (특징점 바운더리)';
COMMENT ON COLUMN branch.branch_id    IS '브랜치 식별 번호';
COMMENT ON COLUMN branch.branch_name  IS '브랜치명';
COMMENT ON COLUMN branch.created_by   IS '생성자';
COMMENT ON COLUMN branch.created_at   IS '생성 일자';
COMMENT ON COLUMN branch.modified_by  IS '수정자';
COMMENT ON COLUMN branch.modified_at  IS '수정 일자';


-- descriptor
CREATE TABLE "descriptor" (
    descriptor_id               BIGSERIAL   NOT NULL,
    face_id                     VARCHAR(36) NOT NULL,
    "descriptor"                BYTEA       NOT NULL,
    descriptor_obtaining_method INTEGER     NULL,
    descriptor_version          INTEGER     NOT NULL,
    descriptor_generation       INTEGER     NOT NULL,
    branch_id                   BIGINT      NOT NULL,
    created_by                  VARCHAR(36) NOT NULL,
    created_at                  TIMESTAMP   NOT NULL,
    modified_by                 VARCHAR(36) NOT NULL,
    modified_at                 TIMESTAMP   NOT NULL,
    PRIMARY KEY (descriptor_id)
);

COMMENT ON TABLE  "descriptor"                                IS '특징점';
COMMENT ON COLUMN "descriptor".descriptor_id                  IS '특징점 식별 번호';
COMMENT ON COLUMN "descriptor".face_id                        IS '얼굴 식별자';
COMMENT ON COLUMN "descriptor"."descriptor"                   IS '추출된 레거시 특징점';
COMMENT ON COLUMN "descriptor".descriptor_obtaining_method    IS '특징점 추출 방법';
COMMENT ON COLUMN "descriptor".descriptor_version             IS '특징점 버전';
COMMENT ON COLUMN "descriptor".descriptor_generation          IS '특징점 세대';
COMMENT ON COLUMN "descriptor".branch_id                      IS '브랜치 식별 번호';
COMMENT ON COLUMN "descriptor".created_by                     IS '생성자';
COMMENT ON COLUMN "descriptor".created_at                     IS '생성 일자';
COMMENT ON COLUMN "descriptor".modified_by                    IS '수정자';
COMMENT ON COLUMN "descriptor".modified_at                    IS '수정 일자';

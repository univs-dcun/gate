-- branch
CREATE TABLE branch (
    branch_id   NUMBER(19,0)    GENERATED ALWAYS AS IDENTITY NOT NULL,
    branch_name VARCHAR2(255)   NOT NULL,
    created_by  VARCHAR2(36)    NOT NULL,
    created_at  TIMESTAMP       NOT NULL,
    modified_by VARCHAR2(36)    NOT NULL,
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
CREATE TABLE "DESCRIPTOR" (
    descriptor_id               NUMBER(19,0)    GENERATED ALWAYS AS IDENTITY NOT NULL,
    face_id                     VARCHAR2(36)    NOT NULL,
    "DESCRIPTOR"                RAW(520)        NOT NULL,
    descriptor_obtaining_method NUMBER(10,0)    NULL,
    descriptor_version          NUMBER(10,0)    NOT NULL,
    descriptor_generation       NUMBER(10,0)    NOT NULL,
    branch_id                   NUMBER(19,0)    NOT NULL,
    created_by                  VARCHAR2(36)    NOT NULL,
    created_at                  TIMESTAMP       NOT NULL,
    modified_by                 VARCHAR2(36)    NOT NULL,
    modified_at                 TIMESTAMP       NOT NULL,
    PRIMARY KEY (descriptor_id)
);

COMMENT ON TABLE  "DESCRIPTOR"                                IS '특징점';
COMMENT ON COLUMN "DESCRIPTOR".descriptor_id                  IS '특징점 식별 번호';
COMMENT ON COLUMN "DESCRIPTOR".face_id                        IS '얼굴 식별자';
COMMENT ON COLUMN "DESCRIPTOR"."DESCRIPTOR"                   IS '추출된 레거시 특징점';
COMMENT ON COLUMN "DESCRIPTOR".descriptor_obtaining_method    IS '특징점 추출 방법';
COMMENT ON COLUMN "DESCRIPTOR".descriptor_version             IS '특징점 버전';
COMMENT ON COLUMN "DESCRIPTOR".descriptor_generation          IS '특징점 세대';
COMMENT ON COLUMN "DESCRIPTOR".branch_id                      IS '브랜치 식별 번호';
COMMENT ON COLUMN "DESCRIPTOR".created_by                     IS '생성자';
COMMENT ON COLUMN "DESCRIPTOR".created_at                     IS '생성 일자';
COMMENT ON COLUMN "DESCRIPTOR".modified_by                    IS '수정자';
COMMENT ON COLUMN "DESCRIPTOR".modified_at                    IS '수정 일자';

-- palm_history definition
CREATE TABLE public.palm_history (
    palm_history_id  BIGSERIAL       NOT NULL,
    transaction_uuid VARCHAR(36)     DEFAULT '',
    palm_id          VARCHAR(255)    DEFAULT '',
    type             VARCHAR(30)     NOT NULL,
    result           BOOLEAN         NOT NULL,
    failure_message  VARCHAR(3000)   DEFAULT '',
    check_liveness   BOOLEAN         NOT NULL,
    created_by       VARCHAR(36)     NOT NULL,
    created_at       TIMESTAMP       NOT NULL,
    modified_by      VARCHAR(36)     NOT NULL,
    modified_at      TIMESTAMP       NOT NULL,
    PRIMARY KEY (palm_history_id)
);

COMMENT ON TABLE  public.palm_history                    IS '팜 이력';
COMMENT ON COLUMN public.palm_history.palm_history_id    IS '팜 이력 시퀀스';
COMMENT ON COLUMN public.palm_history.transaction_uuid   IS '서비스에서 발생한 요청 번호';
COMMENT ON COLUMN public.palm_history.palm_id            IS '팜 아이디';
COMMENT ON COLUMN public.palm_history.type               IS 'ADD, UPDATE, REMOVE, MATCH, LIVENESS, EXTRACT';
COMMENT ON COLUMN public.palm_history.result             IS '요청 성공/실패 유무';
COMMENT ON COLUMN public.palm_history.failure_message    IS '요청 실패 사유';
COMMENT ON COLUMN public.palm_history.check_liveness     IS '라이브니스 적용 여부';
COMMENT ON COLUMN public.palm_history.created_by         IS '등록자';
COMMENT ON COLUMN public.palm_history.created_at         IS '등록일';
COMMENT ON COLUMN public.palm_history.modified_by        IS '수정자';
COMMENT ON COLUMN public.palm_history.modified_at        IS '수정일';


-- palm_liveness definition
CREATE TABLE public.palm_liveness (
    palm_liveness_id BIGSERIAL        NOT NULL,
    palm_history_id  BIGINT           NOT NULL,
    performed        BOOLEAN          NOT NULL,
    passed           BOOLEAN          NOT NULL,
    score            DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_by       VARCHAR(36)      NOT NULL,
    created_at       TIMESTAMP        NOT NULL,
    modified_by      VARCHAR(36)      NOT NULL,
    modified_at      TIMESTAMP        NOT NULL,
    PRIMARY KEY (palm_liveness_id)
);

COMMENT ON TABLE  public.palm_liveness                   IS '팜 라이브니스';
COMMENT ON COLUMN public.palm_liveness.palm_liveness_id  IS '팜 라이브니스 시퀀스';
COMMENT ON COLUMN public.palm_liveness.palm_history_id   IS '팜 이력 시퀀스';
COMMENT ON COLUMN public.palm_liveness.performed         IS '라이브니스 수행 여부';
COMMENT ON COLUMN public.palm_liveness.passed            IS '라이브니스 통과 여부';
COMMENT ON COLUMN public.palm_liveness.score             IS '라이브니스 점수';
COMMENT ON COLUMN public.palm_liveness.created_by        IS '등록자';
COMMENT ON COLUMN public.palm_liveness.created_at        IS '등록일';
COMMENT ON COLUMN public.palm_liveness.modified_by       IS '수정자';
COMMENT ON COLUMN public.palm_liveness.modified_at       IS '수정일';


-- palm_match definition
CREATE TABLE public.palm_match (
    palm_match_id    BIGSERIAL        NOT NULL,
    palm_history_id  BIGINT           NOT NULL,
    palm_id          VARCHAR(255)     DEFAULT '',
    similarity       DOUBLE PRECISION DEFAULT 0,
    threshold        DOUBLE PRECISION DEFAULT 0,
    type             VARCHAR(30)      NOT NULL,
    created_by       VARCHAR(36)      NOT NULL,
    created_at       TIMESTAMP        NOT NULL,
    modified_by      VARCHAR(36)      NOT NULL,
    modified_at      TIMESTAMP        NOT NULL,
    PRIMARY KEY (palm_match_id)
);

COMMENT ON TABLE  public.palm_match                   IS '팜 매치';
COMMENT ON COLUMN public.palm_match.palm_match_id     IS '팜 매치 시퀀스';
COMMENT ON COLUMN public.palm_match.palm_history_id   IS '팜 이력 시퀀스';
COMMENT ON COLUMN public.palm_match.palm_id           IS '매칭된 팜 아이디';
COMMENT ON COLUMN public.palm_match.similarity        IS '매칭 유사도';
COMMENT ON COLUMN public.palm_match.threshold         IS '매칭 임계치';
COMMENT ON COLUMN public.palm_match.type              IS 'IDENTIFY, VERIFY_DESCRIPTOR';
COMMENT ON COLUMN public.palm_match.created_by        IS '등록자';
COMMENT ON COLUMN public.palm_match.created_at        IS '등록일';
COMMENT ON COLUMN public.palm_match.modified_by       IS '수정자';
COMMENT ON COLUMN public.palm_match.modified_at       IS '수정일';

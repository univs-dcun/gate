-- face_history definition
CREATE TABLE faces.public.face_history (
    face_history_id  BIGSERIAL       NOT NULL,
    transaction_uuid VARCHAR(36)     DEFAULT '',
    face_id          VARCHAR(36)     DEFAULT '',
    result           BOOLEAN         NOT NULL,
    type             VARCHAR(30)     NOT NULL,
    failure_message  VARCHAR(3000)   DEFAULT '',
    check_liveness   BOOLEAN         NOT NULL,
    check_multi_face BOOLEAN         NOT NULL,
    created_by       VARCHAR(36)     NOT NULL,
    created_at       TIMESTAMP       NOT NULL,
    modified_by      VARCHAR(36)     NOT NULL,
    modified_at      TIMESTAMP       NOT NULL,
    PRIMARY KEY (face_history_id)
);

COMMENT ON TABLE  faces.public.face_history                   IS '얼굴 이력';
COMMENT ON COLUMN faces.public.face_history.face_history_id   IS '얼굴 이력 시퀀스';
COMMENT ON COLUMN faces.public.face_history.transaction_uuid  IS '서비스에서 발생한 요청 번호(주문 번호 or 적립 번호 or etc)';
COMMENT ON COLUMN faces.public.face_history.face_id           IS '사용자가 전달한 faceId';
COMMENT ON COLUMN faces.public.face_history.result            IS '요청 성공/실패 유무';
COMMENT ON COLUMN faces.public.face_history.type              IS 'ADD, UPDATE, REMOVE, MATCH';
COMMENT ON COLUMN faces.public.face_history.failure_message   IS '요청 실패 사유';
COMMENT ON COLUMN faces.public.face_history.check_liveness    IS '라이브니스 적용 여부';
COMMENT ON COLUMN faces.public.face_history.check_multi_face  IS '다중 얼굴 체크 적용 여부';
COMMENT ON COLUMN faces.public.face_history.created_by        IS '등록자';
COMMENT ON COLUMN faces.public.face_history.created_at        IS '등록일';
COMMENT ON COLUMN faces.public.face_history.modified_by       IS '수정자';
COMMENT ON COLUMN faces.public.face_history.modified_at       IS '수정일';


-- face_liveness definition
CREATE TABLE faces.public.face_liveness (
    face_liveness_id BIGSERIAL       NOT NULL,
    face_history_id  BIGINT          NOT NULL,
    probability      VARCHAR(255)    NOT NULL,
    prdioction       INTEGER         NOT NULL,
    prdioction_desc  VARCHAR(255)    NOT NULL,
    quality          VARCHAR(255)    NOT NULL,
    threshold        VARCHAR(255)    NOT NULL,
    created_by       VARCHAR(36)     NOT NULL,
    created_at       TIMESTAMP       NOT NULL,
    modified_by      VARCHAR(36)     NOT NULL,
    modified_at      TIMESTAMP       NOT NULL,
    PRIMARY KEY (face_liveness_id)
);

COMMENT ON TABLE  faces.public.face_liveness                      IS '라이브니스';
COMMENT ON COLUMN faces.public.face_liveness.face_liveness_id     IS '라이브니스 시퀀스';
COMMENT ON COLUMN faces.public.face_liveness.face_history_id      IS '얼굴 서비스 요청 이력 시퀀스';
COMMENT ON COLUMN faces.public.face_liveness.probability          IS '라이브니스 점수';
COMMENT ON COLUMN faces.public.face_liveness.prdioction           IS '성공 실패 여부 숫자 타입 REAL(0), FAKE(1), UNKNOWN(2), NOFACE(-997)';
COMMENT ON COLUMN faces.public.face_liveness.prdioction_desc      IS '성공 실패 여부 문자열 타입 REAL("real"), FAKE("fake"), UNKNOWN("unknown"), NOFACE("no face")';
COMMENT ON COLUMN faces.public.face_liveness.quality              IS '이미지 퀄리티';
COMMENT ON COLUMN faces.public.face_liveness.threshold            IS '임계치';
COMMENT ON COLUMN faces.public.face_liveness.created_by           IS '등록자';
COMMENT ON COLUMN faces.public.face_liveness.created_at           IS '등록일';
COMMENT ON COLUMN faces.public.face_liveness.modified_by          IS '수정자';
COMMENT ON COLUMN faces.public.face_liveness.modified_at          IS '수정일';


-- face_match definition
CREATE TABLE faces.public.face_match (
    face_match_id   BIGSERIAL       NOT NULL,
    face_history_id BIGINT          NOT NULL,
    face_id         VARCHAR(255)    DEFAULT '',
    similarity      DOUBLE PRECISION DEFAULT 0,
    threshold       DOUBLE PRECISION DEFAULT 0,
    type            VARCHAR(30)     NOT NULL,
    created_by      VARCHAR(36)     NOT NULL,
    created_at      TIMESTAMP       NOT NULL,
    modified_by     VARCHAR(36)     NOT NULL,
    modified_at     TIMESTAMP       NOT NULL,
    PRIMARY KEY (face_match_id)
);

COMMENT ON TABLE  faces.public.face_match                    IS '매치';
COMMENT ON COLUMN faces.public.face_match.face_match_id      IS '매치 시퀀스';
COMMENT ON COLUMN faces.public.face_match.face_history_id    IS '얼굴 서비스 요청 이력 시퀀스';
COMMENT ON COLUMN faces.public.face_match.face_id            IS '매칭된 사용자 페이스 아이디';
COMMENT ON COLUMN faces.public.face_match.similarity         IS '매칭 유사도';
COMMENT ON COLUMN faces.public.face_match.threshold          IS '매칭 임계치';
COMMENT ON COLUMN faces.public.face_match.type               IS 'IDENTIFY (1:N)';
COMMENT ON COLUMN faces.public.face_match.created_by         IS '등록자';
COMMENT ON COLUMN faces.public.face_match.created_at         IS '등록일';
COMMENT ON COLUMN faces.public.face_match.modified_by        IS '수정자';
COMMENT ON COLUMN faces.public.face_match.modified_at        IS '수정일';

ALTER TABLE match_history ADD username VARCHAR2(255) DEFAULT '';

COMMENT ON COLUMN match_history.username IS '사용자 이름';

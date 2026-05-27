ALTER TABLE match_history ADD COLUMN username VARCHAR(255) DEFAULT '';

COMMENT ON COLUMN match_history.username IS '사용자 이름';

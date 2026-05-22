ALTER TABLE match_history ADD consent_snapshot NUMBER(1) NULL;
COMMENT ON COLUMN match_history.consent_snapshot IS '요청 시점의 개인정보 동의 상태';

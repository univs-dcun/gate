ALTER TABLE match_history ADD COLUMN consent_snapshot BOOLEAN NULL;
COMMENT ON COLUMN match_history.consent_snapshot IS '요청 시점의 개인정보 동의 상태';

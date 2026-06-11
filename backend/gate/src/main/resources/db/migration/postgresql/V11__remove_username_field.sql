-- face_feature, palm_feature, match_history: username 컬럼 제거
ALTER TABLE face_feature DROP COLUMN username;
ALTER TABLE palm_feature DROP COLUMN username;
ALTER TABLE match_history DROP COLUMN username;

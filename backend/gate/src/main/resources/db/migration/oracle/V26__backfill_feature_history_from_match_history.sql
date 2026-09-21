-- UG-325: match_history 의 REGISTER 행을 feature_history 로 복사한다 (postgresql 쌍둥이 파일의 주석 참고).
-- 오라클은 '' 가 곧 NULL 이라 NULLIF 가 필요 없다 — COALESCE 만으로 같은 결과가 된다.
-- external_key·feature_seq 를 (feature_id, type) 으로 해석하는 이유는 postgresql 파일 주석 참고.
INSERT INTO feature_history (
    project_id, feature_type, action_type, success, check_liveness, failure_type,
    transaction_uuid, feature_seq, feature_id, user_description, feature_image_path,
    consent_snapshot, external_key,
    created_by, created_at, updated_by, updated_at
)
SELECT
    mh.project_id, mh.feature_type, 'REGISTER', mh.success, mh.check_liveness,
    mh.failure_type,
    mh.transaction_uuid,
    COALESCE((SELECT MAX(bf.biometric_feature_id) FROM biometric_feature bf
               WHERE bf.feature_id = mh.feature_id AND bf.type = mh.feature_type), mh.feature_seq),
    mh.feature_id, mh.user_description,
    COALESCE(mh.feature_image_path, mh.matched_feature_image_path),
    mh.consent_snapshot,
    (SELECT MAX(bf.external_key) FROM biometric_feature bf
      WHERE bf.feature_id = mh.feature_id AND bf.type = mh.feature_type),
    mh.created_by, mh.created_at, mh.updated_by, mh.updated_at
FROM match_history mh
WHERE mh.match_type = 'REGISTER';

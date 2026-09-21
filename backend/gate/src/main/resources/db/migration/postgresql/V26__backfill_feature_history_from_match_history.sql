-- UG-325: match_history 에 REGISTER 로 남아 있던 등록 이력을 feature_history 로 복사한다.
--
-- 원본은 여기서 지우지 않는다. 통합 조회(UG-326)가 배포되기 전까지 기존 목록 API 가 match_history
-- 의 REGISTER 를 계속 읽어야 한다. 이 버전부터 애플리케이션은 두 곳에 함께 쓰고(과도기),
-- 원본 제거는 UG-326 의 마이그레이션이 맡는다. 그래서 이 파일은 INSERT 만 한다.
--
-- feature_image_path: 등록 행은 이미지 경로를 먼저 matched_feature_image_path 에 넣고, 성공한
-- 뒤에야 feature_image_path 에도 채웠다. 실패 행은 앞쪽만 있으므로 둘을 합쳐 읽는다.
-- external_key 는 match_history 에 없어 살아 있는 biometric_feature 에서 가져온다 — 이미 지워진
-- 대상은 NULL 로 남는다.
INSERT INTO feature_history (
    project_id, feature_type, action_type, success, check_liveness, failure_type,
    transaction_uuid, feature_seq, feature_id, user_description, feature_image_path,
    consent_snapshot, external_key,
    created_by, created_at, updated_by, updated_at
)
SELECT
    mh.project_id, mh.feature_type, 'REGISTER', mh.success, mh.check_liveness,
    NULLIF(mh.failure_type, ''),
    mh.transaction_uuid, mh.feature_seq, NULLIF(mh.feature_id, ''), NULLIF(mh.user_description, ''),
    COALESCE(NULLIF(mh.feature_image_path, ''), NULLIF(mh.matched_feature_image_path, '')),
    mh.consent_snapshot, bf.external_key,
    mh.created_by, mh.created_at, mh.updated_by, mh.updated_at
FROM match_history mh
LEFT JOIN biometric_feature bf ON bf.biometric_feature_id = mh.feature_seq
WHERE mh.match_type = 'REGISTER';

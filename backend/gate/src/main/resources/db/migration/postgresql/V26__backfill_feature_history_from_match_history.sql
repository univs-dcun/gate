-- UG-325: match_history 에 REGISTER 로 남아 있던 등록 이력을 feature_history 로 복사한다.
--
-- 원본은 여기서 지우지 않는다. 통합 조회(UG-326)가 배포되기 전까지 기존 목록 API 가 match_history
-- 의 REGISTER 를 계속 읽어야 한다. 이 버전부터 애플리케이션은 두 곳에 함께 쓰고(과도기),
-- 원본 제거는 UG-326 의 마이그레이션이 맡는다. 그래서 이 파일은 INSERT 만 한다.
--
-- feature_image_path: 등록 행은 이미지 경로를 먼저 matched_feature_image_path 에 넣고, 성공한
-- 뒤에야 feature_image_path 에도 채웠다. 실패 행은 앞쪽만 있으므로 둘을 합쳐 읽는다.
-- external_key 는 match_history 에 없어 biometric_feature 에서 가져온다. 조인 키는 feature_seq 가
-- 아니라 (feature_id, type) 이다 — 반박 리뷰 지적. V20 은 feature_seq 에 옛 face_feature_id /
-- palm_feature_id 를 채웠고, V21 은 biometric_feature 를 새 identity 로 만들면서 match_history 의
-- feature_seq 를 재매핑하지 않았다. 그래서 V21 이전 REGISTER 행의 feature_seq 는 biometric_feature_id
-- 가 아니며, palm 의 옛 id 는 face 행 id 와 충돌한다. id 로 조인하면 남의 external_key 를 스냅샷에
-- 박아 넣는다. feature_id(AI 서비스가 발급한 문자열)는 V20 도 백필 키로 쓴 안전한 축이다.
-- 같은 이유로 feature_seq 도 해석되는 경우 새 id 로 바꿔 넣고, 못 찾으면(실패 행 등) 원값을 둔다.
-- MAX() 는 행 증식을 막기 위한 것이다 — (feature_id, type) 은 실질적으로 유일하다.
-- match_history.feature_seq 자체의 낡은 값은 이 파일이 고치지 않는다 (기존 결함, 별도 티켓).
INSERT INTO feature_history (
    project_id, feature_type, action_type, success, check_liveness, failure_type,
    transaction_uuid, feature_seq, feature_id, user_description, feature_image_path,
    consent_snapshot, external_key,
    created_by, created_at, updated_by, updated_at
)
SELECT
    mh.project_id, mh.feature_type, 'REGISTER', mh.success, mh.check_liveness,
    NULLIF(mh.failure_type, ''),
    mh.transaction_uuid,
    COALESCE((SELECT MAX(bf.biometric_feature_id) FROM biometric_feature bf
               WHERE bf.feature_id = mh.feature_id AND bf.type = mh.feature_type), mh.feature_seq),
    NULLIF(mh.feature_id, ''), NULLIF(mh.user_description, ''),
    COALESCE(NULLIF(mh.feature_image_path, ''), NULLIF(mh.matched_feature_image_path, '')),
    mh.consent_snapshot,
    (SELECT MAX(bf.external_key) FROM biometric_feature bf
      WHERE bf.feature_id = mh.feature_id AND bf.type = mh.feature_type),
    mh.created_by, mh.created_at, mh.updated_by, mh.updated_at
FROM match_history mh
WHERE mh.match_type = 'REGISTER';

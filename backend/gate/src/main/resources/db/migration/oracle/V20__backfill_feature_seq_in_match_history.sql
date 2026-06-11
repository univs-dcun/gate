UPDATE match_history mh
SET feature_seq = (
    SELECT ff.face_feature_id
    FROM face_feature ff
    WHERE ff.feature_id = mh.feature_id
    AND ROWNUM = 1
)
WHERE mh.feature_type = 'FACE'
  AND mh.feature_id IS NOT NULL
  AND mh.feature_seq IS NULL;

UPDATE match_history mh
SET feature_seq = (
    SELECT pf.palm_feature_id
    FROM palm_feature pf
    WHERE pf.feature_id = mh.feature_id
    AND ROWNUM = 1
)
WHERE mh.feature_type = 'PALM'
  AND mh.feature_id IS NOT NULL
  AND mh.feature_seq IS NULL;

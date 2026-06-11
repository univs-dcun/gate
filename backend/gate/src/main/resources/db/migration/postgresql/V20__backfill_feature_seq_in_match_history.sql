UPDATE match_history mh
SET feature_seq = ff.face_feature_id
FROM face_feature ff
WHERE mh.feature_type = 'FACE'
  AND mh.feature_id IS NOT NULL
  AND mh.feature_seq IS NULL
  AND ff.feature_id = mh.feature_id;

UPDATE match_history mh
SET feature_seq = pf.palm_feature_id
FROM palm_feature pf
WHERE mh.feature_type = 'PALM'
  AND mh.feature_id IS NOT NULL
  AND mh.feature_seq IS NULL
  AND pf.feature_id = mh.feature_id;

-- UG-328: 인증 시도(match_history)와 특징점 사건(feature_history)이 하나의 시퀀스에서 일련번호를 받는다.
--
-- 로그 상세가 두 테이블을 한 목록으로 보여주는데(UG-326), 화면의 "일련번호" 가 그동안 match_history 의
-- PK 였다. 두 테이블의 PK 는 각자 1부터 오르므로 한 열에 놓으면 유일하지도 시간순도 아니다.
-- 기획의 일련번호는 "이력에 붙는 하나의 시퀀스" 다 — 그러려면 두 테이블이 같은 시퀀스를 써야 한다.
--
-- 기존 행은 created_at 순으로 한 번에 번호를 매기고(동시각은 출처·id 로 고정), 시퀀스는 그 뒤부터 시작한다.
-- 새 행은 컬럼 기본값이 시퀀스에서 받는다 — 애플리케이션은 이 컬럼을 쓰지 않는다 (insertable=false).
CREATE SEQUENCE activity_seq;

ALTER TABLE match_history   ADD COLUMN activity_seq BIGINT;
ALTER TABLE feature_history ADD COLUMN activity_seq BIGINT;

WITH numbered AS (
    SELECT src, id, ROW_NUMBER() OVER (ORDER BY created_at, src, id) AS n
      FROM (SELECT 'M' AS src, match_history_id   AS id, created_at FROM match_history
            UNION ALL
            SELECT 'F' AS src, feature_history_id AS id, created_at FROM feature_history) u
)
UPDATE match_history mh
   SET activity_seq = numbered.n
  FROM numbered
 WHERE numbered.src = 'M' AND numbered.id = mh.match_history_id;

WITH numbered AS (
    SELECT src, id, ROW_NUMBER() OVER (ORDER BY created_at, src, id) AS n
      FROM (SELECT 'M' AS src, match_history_id   AS id, created_at FROM match_history
            UNION ALL
            SELECT 'F' AS src, feature_history_id AS id, created_at FROM feature_history) u
)
UPDATE feature_history fh
   SET activity_seq = numbered.n
  FROM numbered
 WHERE numbered.src = 'F' AND numbered.id = fh.feature_history_id;

-- 시퀀스는 백필한 최댓값 다음부터 (is_called=false 라 다음 nextval 이 그 값을 돌려준다)
SELECT setval('activity_seq',
              (SELECT COALESCE(MAX(s), 0) + 1
                 FROM (SELECT MAX(activity_seq) AS s FROM match_history
                       UNION ALL
                       SELECT MAX(activity_seq) FROM feature_history) x),
              false);

ALTER TABLE match_history   ALTER COLUMN activity_seq SET DEFAULT nextval('activity_seq');
ALTER TABLE match_history   ALTER COLUMN activity_seq SET NOT NULL;
ALTER TABLE feature_history ALTER COLUMN activity_seq SET DEFAULT nextval('activity_seq');
ALTER TABLE feature_history ALTER COLUMN activity_seq SET NOT NULL;

CREATE UNIQUE INDEX ux_match_history_activity_seq   ON match_history(activity_seq);
CREATE UNIQUE INDEX ux_feature_history_activity_seq ON feature_history(activity_seq);

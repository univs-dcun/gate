-- UG-328: 두 이력 테이블이 공유하는 사건 시퀀스 (postgresql 쌍둥이 파일의 주석 참고).
-- 백필은 MERGE(오라클에는 UPDATE ... FROM 이 없다), 시퀀스 재시작은 값이 동적이라 PL/SQL 블록.
CREATE SEQUENCE activity_seq START WITH 1 INCREMENT BY 1 NOCACHE;

ALTER TABLE match_history   ADD (activity_seq NUMBER(20,0));
ALTER TABLE feature_history ADD (activity_seq NUMBER(20,0));

MERGE INTO match_history mh
USING (SELECT id, n
         FROM (SELECT src, id, ROW_NUMBER() OVER (ORDER BY created_at, src, id) AS n
                 FROM (SELECT 'M' AS src, match_history_id   AS id, created_at FROM match_history
                       UNION ALL
                       SELECT 'F' AS src, feature_history_id AS id, created_at FROM feature_history))
        WHERE src = 'M') x
   ON (mh.match_history_id = x.id)
 WHEN MATCHED THEN UPDATE SET mh.activity_seq = x.n;

MERGE INTO feature_history fh
USING (SELECT id, n
         FROM (SELECT src, id, ROW_NUMBER() OVER (ORDER BY created_at, src, id) AS n
                 FROM (SELECT 'M' AS src, match_history_id   AS id, created_at FROM match_history
                       UNION ALL
                       SELECT 'F' AS src, feature_history_id AS id, created_at FROM feature_history))
        WHERE src = 'F') x
   ON (fh.feature_history_id = x.id)
 WHEN MATCHED THEN UPDATE SET fh.activity_seq = x.n;


ALTER TABLE match_history   MODIFY (activity_seq DEFAULT activity_seq.NEXTVAL NOT NULL);
ALTER TABLE feature_history MODIFY (activity_seq DEFAULT activity_seq.NEXTVAL NOT NULL);

CREATE UNIQUE INDEX ux_match_history_activity_seq   ON match_history(activity_seq);
CREATE UNIQUE INDEX ux_feature_history_activity_seq ON feature_history(activity_seq);

-- 시퀀스는 백필한 최댓값 다음부터. 값이 동적이라 PL/SQL. 마이그레이션 중에는 INSERT 가 없어 MODIFY 뒤에 와도 무방하다.
DECLARE
    l_next NUMBER;
BEGIN
    SELECT COALESCE(MAX(s), 0) + 1 INTO l_next
      FROM (SELECT MAX(activity_seq) AS s FROM match_history
            UNION ALL
            SELECT MAX(activity_seq) FROM feature_history);
    EXECUTE IMMEDIATE 'ALTER SEQUENCE activity_seq RESTART START WITH ' || l_next;
END;
/

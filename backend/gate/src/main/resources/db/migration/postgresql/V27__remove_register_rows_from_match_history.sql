-- UG-326: match_history 에 남아 있던 REGISTER 행을 지운다. 등록은 이제 feature_history 의 사건이다.
--
-- V26(UG-325) 이 이 행들을 feature_history 로 복사했고, 그 뒤 애플리케이션은 두 곳에 함께 썼다
-- (과도기). 이 버전부터 등록 경로는 feature_history 에만 쓰고, 목록 조회는 두 테이블을 UNION 으로
-- 읽는다(ActivityLog). 그래서 원본을 지워야 같은 등록이 두 번 보이지 않는다.
--
-- EXISTS 가드: 짝이 되는 feature_history 행(같은 transaction_uuid 의 REGISTER)이 있는 것만 지운다.
-- 짝이 없는 행은 어떤 경로로든 복사가 누락된 것이라 지우면 기록이 사라진다 — 남겨 두고 배포 후
-- 아래 쿼리로 0 건인지 확인한다 (ActivityLog 는 match_history 의 REGISTER 를 읽지 않으므로 남아도
-- 화면에 두 번 나오지는 않는다):
--   SELECT COUNT(*) FROM match_history mh WHERE mh.match_type = 'REGISTER'
--      AND NOT EXISTS (SELECT 1 FROM feature_history fh
--                       WHERE fh.transaction_uuid = mh.transaction_uuid AND fh.action_type = 'REGISTER');
DELETE FROM match_history mh
 WHERE mh.match_type = 'REGISTER'
   AND EXISTS (SELECT 1 FROM feature_history fh
                WHERE fh.transaction_uuid = mh.transaction_uuid
                  AND fh.action_type = 'REGISTER');

-- UG-326: match_history 의 REGISTER 행 제거 (postgresql 쌍둥이 파일의 주석 참고). DML 한 문장.
DELETE FROM match_history mh
 WHERE mh.match_type = 'REGISTER'
   AND EXISTS (SELECT 1 FROM feature_history fh
                WHERE fh.transaction_uuid = mh.transaction_uuid
                  AND fh.action_type = 'REGISTER');

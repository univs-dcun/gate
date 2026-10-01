-- UG-344: 프로젝트당 웹훅 설정 중복 정리 (postgresql 쌍둥이 파일의 배경 참고). 두 방언이 같은 문장이다.
DELETE FROM webhook_configs
 WHERE webhook_config_id IN (
       SELECT webhook_config_id
         FROM (
              SELECT webhook_config_id,
                     ROW_NUMBER() OVER (
                         PARTITION BY project_id
                         ORDER BY updated_at DESC, webhook_config_id DESC
                     ) AS rn
                FROM webhook_configs
              ) ranked
        WHERE ranked.rn > 1
 );

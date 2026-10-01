-- UG-344: 프로젝트당 웹훅 설정 1행을 보장하기 전에 중복을 정리한다 (V38 이 유니크 제약을 건다).
--
-- 엔티티는 @OneToOne 인데 V1 이 project_id 에 유니크 제약을 두지 않았다. 설정이 없는 프로젝트에서 저장을 동시에
-- 두 번 하면 두 트랜잭션이 각각 INSERT 해 행이 둘 생길 수 있었다. 그 뒤로 findByProjectId 가 결과 2건으로
-- 예외를 던져 그 프로젝트는 설정 조회·저장이 500 이 되고 웹훅도 나가지 않는다 — 이미 쓸 수 없는 상태다.
--
-- 남길 행: 가장 최근에 저장된 것(updated_at — V1 부터 NOT NULL), 같으면 id 가 큰 것. 사용자가 마지막으로 저장한
-- 값이다. 나머지는 지운다 — 다른 테이블이 참조하지 않는 설정 행이다. 서명 키(V34)도 잃지 않는다: V34~V38 은 한
-- 번의 배포로 함께 적용되고 V34 는 키를 채우지 않으므로, 이 문장이 도는 시점의 키는 모두 비어 있다.
--
-- 두 방언이 같은 문장이다(불리언 비교가 없다). WebhookConfigDuplicateCleanupSqlTest 가 이 파일을 실행하고 대조한다.
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

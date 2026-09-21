-- UG-326: 로그 상세가 match_history ∪ feature_history 를 created_at 으로 섞어 정렬한다. match_history 에는
-- 인덱스가 하나도 없어(V1 이후 추가된 것 없음) 페이지마다 프로젝트 전체를 훑어 정렬했다. feature_history 는
-- V25 가 (project_id, feature_type, action_type, created_at) 을 가졌으므로 짝을 맞춘다.
-- 반박 리뷰 제안. 기존 결함이지만 UNION 으로 노출 면이 늘어 이번에 넣는다.
CREATE INDEX idx_match_history_project_created ON match_history(project_id, created_at);

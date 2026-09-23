-- UG-303: 프로젝트 삭제 시각 (postgresql 쌍둥이 파일의 배경 참고).
ALTER TABLE projects ADD (deleted_at TIMESTAMP);

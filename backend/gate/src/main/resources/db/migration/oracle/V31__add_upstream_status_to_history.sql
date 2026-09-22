-- UG-294: 하위 서비스 실패의 상태 코드를 이력에 남긴다 (postgresql 쌍둥이 파일의 배경 참고).
ALTER TABLE match_history   ADD (upstream_status NUMBER(10));
ALTER TABLE feature_history ADD (upstream_status NUMBER(10));

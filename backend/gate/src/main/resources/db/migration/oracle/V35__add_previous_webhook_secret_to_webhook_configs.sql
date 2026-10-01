-- UG-344: 재발급 직전의 키. 재발급 후 24시간 함께 서명한다 (postgresql 쌍둥이 파일 참고).
ALTER TABLE webhook_configs ADD (previous_webhook_secret VARCHAR2(100));

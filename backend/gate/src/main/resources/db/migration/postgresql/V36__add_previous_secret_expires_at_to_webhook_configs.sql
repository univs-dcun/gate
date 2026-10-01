-- UG-344: 옛 키로 서명하는 마지막 시각 (UTC). 지나면 옛 키는 서명에 쓰지 않는다 (V35 참고).
ALTER TABLE webhook_configs ADD COLUMN previous_secret_expires_at TIMESTAMP;

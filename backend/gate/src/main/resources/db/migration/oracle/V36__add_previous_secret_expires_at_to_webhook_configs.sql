-- UG-344: 옛 키로 서명하는 마지막 시각 (UTC).
ALTER TABLE webhook_configs ADD (previous_secret_expires_at TIMESTAMP);

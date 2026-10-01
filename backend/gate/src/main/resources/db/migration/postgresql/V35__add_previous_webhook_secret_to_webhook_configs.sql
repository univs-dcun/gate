-- UG-344: 키를 재발급한 직후에는 수신 측이 아직 옛 키를 쓰고 있다. 재발급 후 24시간 동안 옛 키로도 함께 서명해
-- (X-Gate-Signature 에 v1 이 두 개) 키 교체로 수신이 끊기지 않게 한다. 그 옛 키를 담는다.
ALTER TABLE webhook_configs ADD COLUMN previous_webhook_secret VARCHAR(100);

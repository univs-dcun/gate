-- UG-344: 웹훅 서명 키 (postgresql 쌍둥이 파일의 배경 참고). 기존 행은 NULL 이고 앱이 채운다.
ALTER TABLE webhook_configs ADD (webhook_secret VARCHAR2(100));

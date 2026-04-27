# Univs Auth Service

![Version](https://img.shields.io/badge/version-1.0.0-blue)

## Summary

---

게이트웨이 서버에서 인증이 필요한 대상을 해당 서버에서 발급 받은 토큰을 통해  
인가된 사용자인지 확인하고 필요한 서비스에 요청을 전달할 수 있도록합니다.  

## Uses

---

- API URL (Swagger): https://develop.univs.ai:18090/swagger-ui.html
- 접속 후 상단 우측에 Select a definition 박스에서 필요한 서비스 선택

## Caution

해당 인증 서비스는 단순히 가입, 로그인(토큰 발급), 토큰 유효성 체크 역할을 수행합니다.  
각 플랫폼 특성마다 권한이 달라지는 경우를 고려하여 해당 인증 서버에서는 권한을 핸들링하는 로직을 포함하지 않습니다.  

---


## Changelog

---

### [v1.0.2] - 27/04/2026
- Test jenkins (OK)
- Test jenkins SCM (X), give a jenkinsfile path wrong.
- Test jenkins SCM

### [v1.0.1] - 20/03/2026
- Remove redis

### [v1.0.0] - 19/03/2026
- Init
- Documentation Swagger

## License

---

Copyright (c) Universe AI Corporation. All rights reserved.

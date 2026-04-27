# Univs Gate Service

![Version](https://img.shields.io/badge/version-1.0.0-blue)

## 개요

---

이 서비스는 e-kyc 기능을 제공합니다.  
구체적으로는 프로젝트, API Key, 사용자 관리와 인가/비인가를 확인하는 기능을 포함합니다.

## 사용 범위

---

- 클라이언트

## 사용 방법

---

- API URL (Swagger): https://develop.univs.ai:18090/swagger-ui.html
- 접속 후 상단 우측에 Select a definition 박스에서 필요한 서비스 선택

## 주의 사항

---


## 변경 이력 (Changelog)

---

### [v1.0.4] - 2026-03-30
- Billing 분리

### [v1.0.3] - 2026-03-16
- 프로젝트 관련 필드 3개 추가 (projectType, projectModuleType, packageKey)
- projectModuleType 값에 따라 Face/Palm API 엑세스 제어
- 가입할 때 company 생성하도록 코드 수정

### [v1.0.2] - 2026-03-05
- 젠킨스 관련 테스트 2

### [v1.0.1] - 2026-02-26
- ✅ 네트워크 오류로 재 배포

### [v1.0.0] - 2026-02-11
- ✅ 초기 릴리스
- 📝 Swagger 문서화

## License

---

Copyright (c) Universe AI Corporation. All rights reserved.
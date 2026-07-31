# Branch Naming Conventions

## 형식

```
{목적}/{폴더}/{서비스명}/{브랜치 생성 이유}
```

## 구성 요소

| 구성 | 설명 | 예시 |
|------|------|------|
| 목적 | 브랜치 용도 | `feature`, `hotfix`, `chore`, `fix` |
| 폴더 | 작업 영역 | `backend`, `frontend` |
| 서비스명 | 대상 서비스 | `gate`, `face`, `match`, `palm` (스캐폴드 서비스 `auth`/`config`/`gateway`/`discovery`는 msa-scaffold 레포로 분리됨, UG-249) |
| 생성 이유 | 작업 내용 (kebab-case) | `add-auth`, `change-way-of-creating-user` |

## 예시

```
feature/backend/gate/add-file-download-api
feature/backend/match/add-verify-timeout
hotfix/backend/face/fix-liveness-score-null
chore/backend/palm/change-scm-to-github
fix/frontend/gate/fix-user-list-pagination
feature/frontend/auth/add-login-page
```

## 규칙

- 소문자만 사용
- 단어 구분은 `-` (hyphen) 사용
- 서비스명은 실제 폴더명 기준 (`backend/{서비스명}`)

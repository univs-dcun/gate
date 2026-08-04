# Gate API 문서 (외부 공개용)

`gate-api-docs.html` 은 B2B 연동 고객이 보는 Gate Service 의 공식 API 레퍼런스다.
단일 HTML 파일이며 외부 의존성은 같은 폴더의 `logo-vector.svg` 하나뿐이다 (상대 경로 참조).

## 어떻게 서빙되는가

**이 문서는 CI/CD 로 배포되지 않는다.** 운영 서버의 nginx 가 별도 도메인으로 직접 서빙하며,
파일은 사람이 손으로 올린다.

```
https://docs.univsgate.com  →  운영 서버 nginx  →  /var/www/docs-gate/
```

```nginx
server {
    listen 443 ssl;
    server_name docs.univsgate.com;
    root /var/www/docs-gate;

    location / {
        try_files /gate-api-docs.html =404;
    }
    location = /logo-vector.svg {
        try_files $uri =404;
    }
}
```

### 반영 절차

이 폴더의 두 파일을 `/var/www/docs-gate/` 에 복사한다. 서비스 재기동은 필요 없다.

```
gate-api-docs.html
logo-vector.svg
```

**git 에 push 하는 것만으로는 고객이 보는 문서가 바뀌지 않는다.** 서버에 올리는 단계가 별도로 있다.

## 왜 backend/gate 밖에 있는가

원래는 `backend/gate/src/main/resources/static/` 에 있었다 (UG-265 에서 이동).
그 위치에서는 gate-service 이미지에 패키징되지만 게이트웨이에 라우트가 없어
**아무도 읽지 않는 사본**이었고, 대신 두 가지 대가를 치렀다.

1. 문서 한 줄만 고쳐도 gate-service 가 dev·stage·운영에서 재배포됐다 — 실제 서빙되는 문서와는 무관하게
2. `IMAGE_TAG` 계산(`git log -1 -- backend/gate ...`, `.html` 은 제외 대상이 아님)에 참여해
   이미지 태그를 바꿨다. UG-263 · UG-264 사고의 원인이었다

`backend/gate/` 밖으로 나오면서 파이프라인의 변경 감지(`file.startsWith(SERVICE_PATH + '/')`)와
태그 계산 양쪽에서 빠졌다. 이제 이 문서를 고쳐도 서비스는 재배포되지 않는다.

## 브랜치 규칙

배포와 무관해졌지만 **브랜치 규칙은 그대로다** — dev 에서 작업하고 PR 로 승격한다.
특정 브랜치에만 직접 push 하면 세 브랜치가 갈라지고, 그게 UG-263(master 만 669줄 앞섬)과
`frontend/` 표류를 만든 원인이다. 승격 PR 은 이제 재배포를 일으키지 않으므로 비용이 없다.

## 수정 시 주의

- **문서와 코드는 같은 커밋으로 묶는다.** 컨트롤러 매핑을 바꾸는 리팩토링이면 이 파일도 함께 고친다.
  UG-265 는 `ef6befb`(match 패키지 통합) 이후 문서가 방치되어 경로 8개가 404 가 된 건이다.
- **i18n 은 DOM 순서 기반 위치 매핑이다.** `PAGES[pageId].pdsc` 배열의 n 번째 항목이 그 페이지의
  n 번째 `.pdsc` 요소에 들어간다. 표에 행을 추가하면 배열에도 같은 위치에 추가해야 한다.
  빠뜨리면 그 뒤 설명이 전부 한 칸씩 밀린다 (UG-242 에서 실제 발생, UG-265 에서 수정).
- 같은 이유로 `stDesc` 도 `.st-desc` 요소 개수와 맞아야 한다.

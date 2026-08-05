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

## ⚠️ 이 문서는 Face 전용이다 (UG-284)

**Palm 관련 내용을 문서에 넣지 않는다.** Palm API 는 gate service 에 실재하고 정상 동작하지만,
이 문서를 읽는 개발자에게는 존재하지 않는 것처럼 보이게 한다.

### 그래서 실무에서 무엇이 달라지는가

| 코드 변경 | 문서 갱신 |
|---|---|
| Face 관련 API 변경 | **필요** — 같은 PR 에서 함께 고친다 |
| Palm 관련 API 변경 | **불필요** — 문서에 해당 내용이 없다 |
| Face·Palm 공용 API 변경 (매칭 이력 등) | 필요. 단 **Palm 을 드러내는 서술은 빼고** 쓴다 |

세 번째가 실수하기 쉽다. 공용 API 를 정확히 쓰려다 보면 자연스럽게 Palm 이 새어 나온다.
아래 표현들이 대표적이다.

- `featureType` 의 값 목록에 `PALM` — `(FACE | PALM | ALL)` 이 아니라 `(FACE | ALL)`
- "얼굴·팜", "Face and palm", "두 모듈"
- `palmFeatureId`, `palmId` 같은 필드명 언급
- `CODE_MSGS` 의 Palm 오류 메시지

이 규칙은 **사용자가 "palm 문서를 만들어 달라"고 명시적으로 요청할 때까지** 유효하다.

### 무엇을 지웠는가

UG-279 에서 주석 처리했다가 UG-284 에서 소스에서 완전히 삭제했다. 주석으로는 소스 보기(Ctrl+U)
에 그대로 노출되어 목적이 달성되지 않았다.

| 종류 | 내용 |
|---|---|
| nav | `FEATURE` 그룹, `FaceID 조회`, `PALM FEATURE`, `PALM MATCH` |
| INTRO | 팜 정맥 카드 (HTML + `INTRO.{ko,en}.cards`) |
| page | 통합 목록 조회, FaceID 조회, PALM FEATURE 5종, PALM MATCH 2종 |
| nav i18n | ko/en 각 6키 |
| PAGES | `p-feature-list`, `p-face-feature-get-by-faceid`, `p-palm-*` 7개 |
| 기타 | `featureSeq` 의 `palmFeatureId` 언급, `CODE_MSGS` Palm 메시지 3건 |

`통합 목록 조회`(`GET /api/v1/feature`)를 뺀 이유는 그 페이지 제목이 "특징점 통합 목록 조회
(Face + Palm)" 이고 `featureType` 이 `PALM` 을 값으로 노출해, 페이지가 있는 것만으로 Palm 모듈의
존재가 드러나기 때문이다.

### 복원하려면

`git log --oneline -- docs/api/gate-api-docs.html` 에서 UG-284 커밋(`d2ffb4e`) 직전 상태를 꺼낸다.
`git show 9b6a3a0:docs/api/gate-api-docs.html` 이 삭제 직전(주석 상태) 사본이다.

### 한 페이지를 뺄 때 함께 지워야 하는 네 곳

하나라도 남으면 nav 는 있는데 페이지가 없거나(빈 화면), 위치 기반 i18n 이 한 칸씩 밀린다.

1. 사이드바 `<li class="sb-item" onclick="show('...')">` — 그룹에 항목이 하나뿐이면 `.sb-sec` 째
2. `<div class="page" id="p-...">` 블록 전체
3. nav i18n 키 — `ko` 와 `en` **양쪽**
4. `PAGES['p-...']` 엔트리

INTRO 카드를 뺄 때는 HTML `.info-card` 와 `INTRO.ko.cards` / `INTRO.en.cards` 세 곳이 같은
인덱스에서 함께 빠져야 한다. 한쪽만 빼면 뒤 카드(JWT / API Key)에 엉뚱한 설명이 들어간다.

### 검증

```bash
# 1) Palm 잔여 0건 (주석까지 포함해 소스 전체)
grep -c 'PALM\|팜\|[Pp]alm' docs/api/gate-api-docs.html

# 2) 인라인 스크립트 문법
node --check <(python3 -c "
import re,pathlib
t=pathlib.Path('docs/api/gate-api-docs.html').read_text()
print('\n;\n'.join(re.findall(r'<script[^>]*>(.*?)</script>', t, re.S)))
")
```

3) 브라우저로 열어 nav 가 `AUTH / FACE FEATURE / FACE MATCH / HISTORY` 인지, 한/영 토글이
   정상인지, 콘솔 에러가 없는지 확인한다.

## 수정 시 주의

- **문서와 코드는 같은 커밋으로 묶는다.** 컨트롤러 매핑을 바꾸는 리팩토링이면 이 파일도 함께 고친다.
  UG-265 는 `ef6befb`(match 패키지 통합) 이후 문서가 방치되어 경로 8개가 404 가 된 건이다.
- **i18n 은 DOM 순서 기반 위치 매핑이다.** `PAGES[pageId].pdsc` 배열의 n 번째 항목이 그 페이지의
  n 번째 `.pdsc` 요소에 들어간다. 표에 행을 추가하면 배열에도 같은 위치에 추가해야 한다.
  빠뜨리면 그 뒤 설명이 전부 한 칸씩 밀린다 (UG-242 에서 실제 발생, UG-265 에서 수정).
- 같은 이유로 `stDesc` 도 `.st-desc` 요소 개수와 맞아야 한다.

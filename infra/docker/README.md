# infra/docker — 배포 compose 구성 (UG-235, UG-246)

```
infra/docker/
├── compose/  ★ 공유 compose 레이어 파일 세트 (UG-246) — 이것이 진실
├── dev/      개발 서버 192.168.0.135 — .env.example
├── stage/    스테이징 서버 192.168.0.136 — .env.example
└── master/   운영 서버 192.168.79.8 — .env.example
```

## 서버 표준 디렉토리 구조 (UG-247)

세 서버 공통 (계정만 환경별로 다름 — 현재 WORKING_DIR은 deploy-targets/{env}.env가 진실):

```
/home/{계정}/platform/gate/     ← WORKING_DIR (여기서 docker compose 실행)
├── .env                        ← COMPOSE_FILE 포함 (진실은 서버)
├── compose.*.yml               ← compose/ 폴더에서 복사 (진실은 레포)
├── logs/
├── docker-backup/              ← postgresql_data, redis_data, uploads
├── spring-config/              ← native 전환 대비 (gate-config 클론으로 준비)
└── license/                    ← 라이선스 파일류
```

⚠️ 레이어 compose 파일의 마운트는 `./` 기준 — **반드시 이 표준 구조와 함께** 사용한다.
(구 중첩 구조 `docker/config`에서는 `../`였음 — 혼용 불가)

## 레이어 구조 (UG-246, 스캐폴드 분리 준비)

compose가 소유권 경계대로 분해되어 있다. 조합은 각 서버 `.env`의 `COMPOSE_FILE`이 결정한다.

| 파일 | 레이어 | 서비스 |
|---|---|---|
| `compose.core.yml` | 스캐폴드 core | config-server, discovery-server, gateway-server |
| `compose.infra.yml` | 공유 인프라 | platform-postgresql, redis |
| `compose.auth.yml` | 범용 모듈 | auth-service |
| `compose.notify.yml` | 범용 모듈 | notify-service |
| `compose.face.yml` | 생체인증 역량 (face 세트) | face-service, match-server, fxp-preprocess-service |
| `compose.palm.yml` | 생체인증 역량 (palm 세트) | palm-service |
| `compose.gate.yml` | 제품 | gate-service |
| `compose.demo-web.yml` | 제품 | demo-web |
| `compose.gate-web.yml` | 제품 | gate-web (dev 미사용) |

**실행 방식**: 서버 `.env`에 `COMPOSE_PROJECT_NAME=gate` + `COMPOSE_FILE=<콜론 구분 목록>`이
선언되어 있어, docker compose가 자동으로 읽는다. 실행 명령은 분해 이전과 동일:

```bash
docker compose -p gate up -d <service>
```

환경 간 차이(호스트 포트, URL)는 전부 `.env` 변수로 이동했다 — compose 파일 세트는
세 환경이 **동일한 파일**을 쓴다. 기본값은 dev 기준이고 master가 `.env`에서 포트만
오버라이드한다(`*_HOST_PORT=7xxx`). 네트워크는 세 환경 모두 `platform-net`으로 통일됨
(UG-247에서 운영의 gate-net 폐기 — `DOCKER_NETWORK` 변수는 특수 배포용으로만 남아 있음).

## 규칙

- **compose 파일의 진실은 `compose/` 폴더다.** 서버에는 이 파일들을 복사해서 쓴다.
  compose 구성 변경은 여기서 커밋 → 각 서버 WORKING_DIR에 반영.
- `.env`의 진실은 서버다. `.env.example`은 백업/diff용 스냅샷 — 서버의 `.env` 구성을 변경하면
  example에도 반영하여 커밋한다.
- `.env.example`의 `<SECRET>` 값은 git에 저장하지 않는다.
- `*_VERSION` 값은 Jenkins 배포가 sed로 갱신하는 살아있는 값 — 스냅샷 시점 예시일 뿐.
  diff 시 버전 라인은 무시할 것.

## 재해 복구 절차

1. `compose/*.yml` 전체를 서버 작업 디렉토리로 복사
2. 해당 환경의 `.env.example`을 `.env`로 복사 후 `<SECRET>` 값 채우기
3. `*_VERSION`을 마지막 배포 버전으로 갱신 (레지스트리 태그 목록 또는 Slack 배포 알림 참고)
4. `docker network create platform-net` 후 `docker compose -p gate up -d`

## 참고

- 세 서버 모두 2026-07-30에 레이어 구성 + 표준 디렉토리로 전환 완료 (UG-246/247).
  구 단일 docker-compose.yml 3벌은 제거됨 — 필요 시 git 이력에서 복구 가능.
- 전환 과정에서 postgres 서비스명(`platform-postgresql`)과 네트워크명(`platform-net`)이
  세 환경 공통으로 통일됨.
- config-server는 git 모드로 동작 중이지만 세 서버 모두 `./spring-config:/config-repo` 볼륨을
  마운트하고 있음 (native 전환 대비용, 현재는 읽히지 않음 — 내용물이 낡았을 수 있으니 native
  전환 시 반드시 gate-config 최신본으로 교체).

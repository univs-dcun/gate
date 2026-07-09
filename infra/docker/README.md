# infra/docker — 서버별 배포 구성 스냅샷 (UG-235)

각 환경 서버에서 실행 중인 docker-compose 구성의 **백업·diff용 스냅샷**이다.

```
infra/docker/
├── dev/      개발 서버 192.168.0.135  (/home/univs/platform/docker/config)
├── stage/    스테이징 서버 192.168.0.136
└── master/   운영 서버 192.168.79.8   (/home/psh/gate/docker)
```

## 규칙 (1단계: 서버가 진실)

- **현재 진실은 서버의 파일이다.** 이 폴더는 백업/비교/복구용 스냅샷.
- 서버의 compose/.env 구성을 변경하면 **이 폴더에도 반영하여 커밋**한다 (변경 이력 확보).
- `.env.example`의 `<SECRET>` 값은 git에 저장하지 않는다. 실제 값은 서버의 `.env`에만 존재.
- `*_VERSION` 값은 Jenkins 배포가 sed로 갱신하는 살아있는 값 — 스냅샷 시점 예시일 뿐, 서버의 현재 값과 다를 수 있다. diff 시 버전 라인은 무시할 것.

## 재해 복구 절차

1. 해당 환경 폴더의 `docker-compose.yml`을 서버 작업 디렉토리로 복사
2. `.env.example`을 `.env`로 복사 후 `<SECRET>` 값 채우기
3. `*_VERSION`을 마지막 배포 버전으로 갱신 (레지스트리 태그 목록 또는 Slack 배포 알림 참고)
4. `docker network create platform-net` (external 네트워크) 후 `docker compose -p gate up -d`

## 환경 간 구조적 차이 (2026-07-08 스냅샷 기준)

| 항목 | dev | stage | master(운영) |
|---|---|---|---|
| 네트워크 | `platform-net` | `platform-net` | **`gate-net`** |
| PostgreSQL 서비스명 | `platform-postgresql` | `platform-postgresql` | **`postgresql`** (CORE_DB_URL도 다름) |
| 외부 포트 | 표준 (5432, 8888, 8080…) | 표준 | **7xxx 대역** (7432, 7888, 7080…) |
| gate-web | **없음** | 3000 | 7751 |
| demo-web | 3001 | 3001 | 7750 |
| fxp-preprocess env 표기 | UPPER_SNAKE | dot-notation | dot-notation |

- 운영 서버는 다른 서비스와 공존하는 장비라 포트 충돌 회피를 위해 7xxx 대역 사용으로 추정.
- config-server는 git 모드로 동작 중이지만 세 서버 모두 `../spring-config:/config-repo` 볼륨을 마운트하고 있음 (native 전환 대비용, 현재는 읽히지 않음 — 내용물이 낡았을 수 있으니 native 전환 시 반드시 gate-config 최신본으로 교체).

## 2단계 (미착수)

레포를 진실로 역전(배포 파이프라인이 레포의 compose를 서버로 복사)하는 것은 별도 결정.

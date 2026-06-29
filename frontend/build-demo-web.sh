#!/bin/bash
set -e

# ================================================================
# demo-web 빌드 및 Docker registry push 자동화 스크립트
#
# 사용법:
#   REGISTRY=registry.example.com ./frontend/build-demo-web.sh <버전>
#
# 예시:
#   REGISTRY=192.168.0.10:5000 ./frontend/build-demo-web.sh 1.0.1
# ================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
DEMO_WEB_DIR="${REPO_ROOT}/frontend/demo-web"
CONFIG_DIR="${REPO_ROOT}/frontend/demo-web-config"
SERVICE="demo-web"
VERSION="${1:-}"

# ── 유효성 검사 ────────────────────────────────────────────────
if [ -z "$REGISTRY" ]; then
  echo "[오류] REGISTRY 환경변수를 설정해주세요."
  echo "  예) REGISTRY=registry.example.com ./frontend/build-demo-web.sh 1.0.0"
  exit 1
fi

if [ -z "$VERSION" ]; then
  echo "[오류] 배포 버전을 인수로 전달해주세요."
  echo "  예) REGISTRY=registry.example.com ./frontend/build-demo-web.sh 1.0.0"
  exit 1
fi

if ! docker buildx version &>/dev/null; then
  echo "[오류] docker buildx를 사용할 수 없습니다. Docker Desktop 또는 buildx 플러그인을 설치해주세요."
  exit 1
fi

echo ""
echo "========================================"
echo "  demo-web 빌드 및 배포"
echo "  버전      : ${VERSION}"
echo "  레지스트리 : ${REGISTRY}"
echo "========================================"

# ── 1단계: 서브모듈 최신화 ─────────────────────────────────────
echo ""
echo "[1/4] 원본 코드(submodule) 업데이트 중..."
cd "${REPO_ROOT}"
git submodule update --remote --merge frontend/demo-web
COMMIT=$(git -C "${DEMO_WEB_DIR}" rev-parse --short HEAD)
echo "  → 원본 최신 커밋: ${COMMIT}"

# ── 2단계: 커스텀 파일 덮어쓰기 ────────────────────────────────
echo ""
echo "[2/4] 커스텀 파일 적용 중..."
cp -f "${CONFIG_DIR}/Dockerfile"            "${DEMO_WEB_DIR}/Dockerfile"
cp -f "${CONFIG_DIR}/server-https.js"       "${DEMO_WEB_DIR}/server-https.js"
cp -f "${CONFIG_DIR}/docker-entrypoint.sh"  "${DEMO_WEB_DIR}/docker-entrypoint.sh"
cp -f "${CONFIG_DIR}/docker-compose.yml"    "${DEMO_WEB_DIR}/docker-compose.yml"
echo "  → Dockerfile, server-https.js, docker-entrypoint.sh, docker-compose.yml 적용 완료"

cp -rf "${SCRIPT_DIR}/Reference/"           "${DEMO_WEB_DIR}/Reference/"
echo "  → Reference/ (로컬 npm 패키지) 적용 완료"

# ── 3단계: Docker 이미지 빌드 및 push ──────────────────────────
echo ""
echo "[3/4] Docker 이미지 빌드 중 (linux/amd64)..."
docker buildx build \
  --platform linux/amd64 \
  --tag "${REGISTRY}/${SERVICE}:${VERSION}" \
  --tag "${REGISTRY}/${SERVICE}:latest" \
  --no-cache \
  --push \
  "${DEMO_WEB_DIR}"

# ── 4단계: 완료 ────────────────────────────────────────────────
echo ""
echo "[4/4] 배포 완료!"
echo "  이미지: ${REGISTRY}/${SERVICE}:${VERSION}"
echo "  이미지: ${REGISTRY}/${SERVICE}:latest"
echo ""
echo "서버에서 실행:"
echo "  docker compose pull ${SERVICE} && docker compose up -d ${SERVICE}"

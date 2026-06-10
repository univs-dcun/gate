#!/bin/bash
set -e

# ================================================================
# gate-web 빌드 및 Docker registry push 자동화 스크립트
#
# 사용법:
#   REGISTRY=registry.example.com ./frontend/build-gate-web.sh <버전>
#
# 예시:
#   REGISTRY=dockhub.univs.ai:9870/univs-gate ./frontend/build-gate-web.sh 1.0.0
# ================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
GATE_WEB_DIR="${REPO_ROOT}/frontend/gate-web"
CONFIG_DIR="${REPO_ROOT}/frontend/gate-web-config"
SERVICE="gate-web"
VERSION="${1:-}"

# ── 유효성 검사 ────────────────────────────────────────────────
if [ -z "$REGISTRY" ]; then
  echo "[오류] REGISTRY 환경변수를 설정해주세요."
  echo "  예) REGISTRY=registry.example.com ./frontend/build-gate-web.sh 1.0.0"
  exit 1
fi

if [ -z "$VERSION" ]; then
  echo "[오류] 배포 버전을 인수로 전달해주세요."
  echo "  예) REGISTRY=registry.example.com ./frontend/build-gate-web.sh 1.0.0"
  exit 1
fi

if ! docker buildx version &>/dev/null; then
  echo "[오류] docker buildx를 사용할 수 없습니다. Docker Desktop 또는 buildx 플러그인을 설치해주세요."
  exit 1
fi

echo ""
echo "========================================"
echo "  gate-web 빌드 및 배포"
echo "  버전      : ${VERSION}"
echo "  레지스트리 : ${REGISTRY}"
echo "========================================"

# ── 1단계: 커스텀 파일 덮어쓰기 ────────────────────────────────
echo ""
echo "[1/3] 커스텀 파일 적용 중..."
cp -f "${CONFIG_DIR}/Dockerfile"           "${GATE_WEB_DIR}/Dockerfile"
cp -f "${CONFIG_DIR}/nginx.conf"           "${GATE_WEB_DIR}/nginx.conf"
cp -f "${CONFIG_DIR}/docker-entrypoint.sh" "${GATE_WEB_DIR}/docker-entrypoint.sh"
echo "  → Dockerfile, nginx.conf, docker-entrypoint.sh 적용 완료"

# ── 2단계: Docker 이미지 빌드 및 push ──────────────────────────
echo ""
echo "[2/3] Docker 이미지 빌드 중 (linux/amd64)..."
docker buildx build \
  --platform linux/amd64 \
  --tag "${REGISTRY}/${SERVICE}:${VERSION}" \
  --tag "${REGISTRY}/${SERVICE}:latest" \
  --no-cache \
  --push \
  "${GATE_WEB_DIR}"

# ── 3단계: 완료 ────────────────────────────────────────────────
echo ""
echo "[3/3] 배포 완료!"
echo "  이미지: ${REGISTRY}/${SERVICE}:${VERSION}"
echo "  이미지: ${REGISTRY}/${SERVICE}:latest"
echo ""
echo "서버에서 실행:"
echo "  docker compose pull ${SERVICE} && docker compose up -d ${SERVICE}"

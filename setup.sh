#!/bin/bash
set -e

# ================================================================
# gate 레포 초기 세팅 스크립트
#
# 사용법:
#   git clone https://github.com/univs-dcun/gate.git
#   cd gate
#   ./setup.sh
# ================================================================

echo ""
echo "========================================"
echo "  gate 레포 초기 세팅"
echo "========================================"

# ── submodule 초기화 ────────────────────────────────────────────
echo ""
echo "[1/2] submodule 초기화 중..."
git submodule update --init --recursive
echo "  → frontend/demo-web (hjkim-univsai/face-auth-demo) 완료"

# ── 스크립트 실행 권한 부여 ─────────────────────────────────────
echo ""
echo "[2/2] 스크립트 실행 권한 부여 중..."
chmod +x frontend/build-demo-web.sh
echo "  → frontend/build-demo-web.sh 완료"

echo ""
echo "========================================"
echo "  세팅 완료!"
echo ""
echo "  demo-web 배포:"
echo "  REGISTRY=<레지스트리 주소> ./frontend/build-demo-web.sh <버전>"
echo "========================================"

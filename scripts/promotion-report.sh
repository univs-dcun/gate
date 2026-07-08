#!/bin/bash
# ================================================================
# 승격(promotion) 전 변경 리포트 (UG-234)
#
# dev→stage 또는 stage→master merge 전에 실행하여
# "이번 승격으로 무엇이 나가는지"를 확인하는 읽기 전용 도구.
# 아무것도 수정/적용하지 않는다.
#
# 사용법:
#   ./scripts/promotion-report.sh stage    # dev → stage 분석
#   ./scripts/promotion-report.sh master   # stage → master 분석
#
# 승격 절차:
#   1) 이 리포트 확인 (❌/⚠️ 항목 해소)
#   2) GitHub PR 생성 (dev→stage 또는 stage→master)
#   3) GitHub에서 merge → Jenkins가 자동 배포
#      (로컬 merge + push는 배포가 트리거되지 않음!)
# ================================================================
set -euo pipefail

REGISTRY="dockhub.univs.ai:9870"
REPO_PREFIX="univs-gate"

TARGET="${1:-}"
case "$TARGET" in
  stage)  SOURCE="dev" ;;
  master) SOURCE="stage" ;;
  *) echo "사용법: $0 stage|master"; exit 1 ;;
esac

# 테스트/과거 시점 분석용 오버라이드 (일반 사용 시 불필요)
SOURCE_REF="${SOURCE_REF:-origin/$SOURCE}"
TARGET_REF="${TARGET_REF:-origin/$TARGET}"

cd "$(git rev-parse --show-toplevel)"
git fetch origin --quiet

MERGE_BASE=$(git merge-base "$TARGET_REF" "$SOURCE_REF")
CHANGED_FILES=$(git diff --name-only "$MERGE_BASE" "$SOURCE_REF")

echo ""
echo "================================================================"
echo "  승격 리포트: $SOURCE → $TARGET"
echo "  기준: $(git rev-parse --short "$TARGET_REF") ($TARGET) ← $(git rev-parse --short "$SOURCE_REF") ($SOURCE)"
echo "================================================================"

if [ -z "$CHANGED_FILES" ]; then
  echo ""
  echo "변경 없음 — 승격할 내용이 없습니다."
  exit 0
fi

DEPLOY_COUNT=0
IMAGE_MISSING=0
MIGRATION_FOUND=0

echo ""
echo "📦 재배포 대상 서비스"
echo "----------------------------------------------------------------"

for dir in backend/*/; do
  svc_path="${dir%/}"
  jenkinsfile="$svc_path/Jenkinsfile"
  [ -f "$jenkinsfile" ] || continue

  # 파이프라인의 변경 감지 필터와 동일 규칙 (Jenkinsfile, *.md 제외)
  relevant=$(echo "$CHANGED_FILES" | grep "^$svc_path/" | grep -v 'Jenkinsfile$' | grep -v '\.md$' || true)
  [ -z "$relevant" ] && continue

  DEPLOY_COUNT=$((DEPLOY_COUNT + 1))
  service_name=$(grep -o "SERVICE_NAME[[:space:]]*=[[:space:]]*'[^']*'" "$jenkinsfile" | head -1 | sed "s/.*'\(.*\)'/\1/")

  # 파이프라인과 동일한 서비스 스코프 태그 계산 (변경 감지 필터와 동일하게 Jenkinsfile/md 제외)
  hash=$(git log --no-merges -1 --pretty=format:'%h' "$SOURCE_REF" -- "$svc_path" ":!$svc_path/Jenkinsfile" ":!$svc_path/*.md")
  cdate=$(git log --no-merges -1 --pretty=format:'%cd' --date=format:'%Y%m%d' "$SOURCE_REF" -- "$svc_path" ":!$svc_path/Jenkinsfile" ":!$svc_path/*.md")
  tag="$cdate-$hash"
  image="$REGISTRY/$REPO_PREFIX/$service_name:$tag"

  # 레지스트리에 이미지 존재 확인
  if command -v docker >/dev/null 2>&1; then
    if err=$(docker manifest inspect "$image" 2>&1 >/dev/null); then
      img_status="✅ 이미지 존재"
    elif echo "$err" | grep -qi "manifest unknown\|not found\|no such manifest"; then
      img_status="❌ 이미지 없음 ← $SOURCE 빌드 확인 필요!"
      IMAGE_MISSING=$((IMAGE_MISSING + 1))
    else
      img_status="⚠️ 확인 불가 (docker login $REGISTRY 필요?)"
    fi
  else
    img_status="⚠️ 확인 불가 (docker 미설치)"
  fi

  printf "  %-18s %-22s %s\n" "$service_name" "$tag" "$img_status"

  # Flyway 마이그레이션 변경 검출 (신규 = 스키마 변경, 수정/삭제 = 적용된 환경에서 checksum 오류 위험)
  migrations=$(git diff --name-status "$MERGE_BASE" "$SOURCE_REF" -- "$svc_path/src/main/resources/db/migration/" || true)
  if [ -n "$migrations" ]; then
    MIGRATION_FOUND=$((MIGRATION_FOUND + 1))
    echo "$migrations" | while IFS=$'\t' read -r st f1 f2; do
      fname="${f2:-$f1}"; fname="${fname##*/}"
      case "$st" in
        A*) label="신규" ;;
        M*) label="수정 ⚠️ (적용된 환경은 checksum 오류 위험)" ;;
        D*) label="삭제 ⚠️ (적용된 환경은 validate 실패 위험)" ;;
        R*) label="이름변경 ⚠️" ;;
        *)  label="$st" ;;
      esac
      echo "      🗄  마이그레이션 $label: $fname"
    done
  fi

  # 포함 커밋 요약 (최근 10개)
  git log --oneline --no-merges -n 10 "$TARGET_REF..$SOURCE_REF" -- "$svc_path" | sed 's/^/      /'
  total_commits=$(git rev-list --count --no-merges "$TARGET_REF..$SOURCE_REF" -- "$svc_path")
  if [ "$total_commits" -gt 10 ]; then
    echo "      ... 외 $((total_commits - 10))개 커밋"
  fi
  echo ""
done

if [ "$DEPLOY_COUNT" -eq 0 ]; then
  echo "  (없음 — 서비스 코드 변경이 포함되지 않은 승격)"
  echo ""
fi

# 서비스 외 변경 (배포에는 영향 없음)
OTHER=$(echo "$CHANGED_FILES" | grep -v '^backend/' || true)
if [ -n "$OTHER" ]; then
  echo "📄 서비스 외 변경 (배포 없음)"
  echo "----------------------------------------------------------------"
  echo "$OTHER" | cut -d/ -f1 | sort | uniq -c | awk '{printf "  %s개 파일: %s\n", $1, $2}'
  echo ""
fi

echo "================================================================"
echo "  요약: 재배포 ${DEPLOY_COUNT}개 서비스 / DB 마이그레이션 ${MIGRATION_FOUND}건 / 이미지 누락 ${IMAGE_MISSING}건"
if [ "$IMAGE_MISSING" -gt 0 ]; then
  echo "  ❌ 이미지 누락이 있습니다. $SOURCE 브랜치의 해당 서비스 빌드부터 확인하세요."
fi
if [ "$MIGRATION_FOUND" -gt 0 ]; then
  echo "  ⚠️  DB 마이그레이션이 포함되어 있습니다. $TARGET DB 백업 확인 후 진행하세요."
fi
echo "  참고: Spring 설정 변경은 gate-config 레포에서 별도 관리됩니다 (승격과 무관하게 즉시 적용)."
echo "================================================================"

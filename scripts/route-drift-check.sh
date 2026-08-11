#!/usr/bin/env bash
#
# 게이트웨이 라우트 표 드리프트 점검 (UG-275)
#
# gate-config 의 gateway-server-{dev,stag,prod,onpremise}.yml 네 파일을 대조해
# 한 파일에만 있거나 빠진 라우트를 찾는다.
#
# 왜 필요한가:
#   /api/v1/feature/** 라우트가 onpremise 파일에만 없었다. 세 파일에는 있었고,
#   그래서 온프레미스 구성에서는 특징점 등록·매칭·라이브니스가 전부 404 였다.
#   제품의 핵심 기능인데 납품 전까지 아무도 몰랐다 — 한 파일에만 라우트를 추가해도
#   드러나는 장치가 없었기 때문이다.
#
#   같은 이유로 죽은 라우트도 쌓인다. 점검 결과 /api/v1/api-keys, /api/v1/users,
#   /api/v1/sdk, /api/v1/messages 는 backend 전체에 매핑이 0건이었다.
#
# 사용법:
#   scripts/route-drift-check.sh [gate-config 경로] [모노레포 backend 경로]
#
#   기본값은 이 레포와 나란히 클론된 ../gate-config 그리고 ./backend 다.
#
# 종료 코드:
#   0  드리프트 없음
#   1  드리프트 발견 (표로 출력)
#   2  입력 문제 (경로 없음 등)

set -euo pipefail

CONFIG_DIR="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/../gate-config}"
BACKEND_DIR="${2:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/backend}"

ENVS=(dev stag prod onpremise)

if [ ! -d "$CONFIG_DIR" ]; then
  echo "gate-config 경로를 찾을 수 없다: $CONFIG_DIR" >&2
  echo "사용법: $0 [gate-config 경로] [backend 경로]" >&2
  exit 2
fi

for env in "${ENVS[@]}"; do
  f="$CONFIG_DIR/gateway-server-$env.yml"
  [ -f "$f" ] || { echo "설정 파일이 없다: $f" >&2; exit 2; }
done

echo "================================================================"
echo "  게이트웨이 라우트 드리프트 점검 (UG-275)"
echo "  설정: $CONFIG_DIR"
echo "================================================================"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# 각 환경의 "라우트 id → Path 술어" 목록을 뽑는다.
# yq 에 의존하지 않는다 (온프레미스 점검 장비에 없을 수 있음).
for env in "${ENVS[@]}"; do
  awk '
    /^[[:space:]]*-[[:space:]]*id:[[:space:]]*/ {
      id = $0
      sub(/^[[:space:]]*-[[:space:]]*id:[[:space:]]*/, "", id)
      sub(/[[:space:]]*$/, "", id)
      uri = ""
      next
    }
    /^[[:space:]]*uri:[[:space:]]*/ && id != "" {
      uri = $0
      sub(/^[[:space:]]*uri:[[:space:]]*/, "", uri)
      sub(/[[:space:]]*$/, "", uri)
      sub(/^lb:(ws:)?\/\//, "", uri)
      next
    }
    /^[[:space:]]*-[[:space:]]*Path=/ && id != "" {
      p = $0
      sub(/^[[:space:]]*-[[:space:]]*Path=/, "", p)
      sub(/[[:space:]]*$/, "", p)
      print id "\t" p "\t" uri
      id = ""
    }
  ' "$CONFIG_DIR/gateway-server-$env.yml" | sort > "$tmp/$env.tsv"
done

# ── 1) 네 환경에 모두 있지 않은 라우트 id
cut -f1 "$tmp"/*.tsv | sort -u > "$tmp/all-ids"

drift=0
missing_report=""
while read -r id; do
  present=""
  absent=""
  for env in "${ENVS[@]}"; do
    if cut -f1 "$tmp/$env.tsv" | grep -qx "$id"; then
      present="$present $env"
    else
      absent="$absent $env"
    fi
  done
  if [ -n "$absent" ]; then
    missing_report="$missing_report\n  $id\n      있음:$present\n      없음:$absent"
    drift=1
  fi
done < "$tmp/all-ids"

if [ -n "$missing_report" ]; then
  echo
  echo "⚠️  일부 환경에만 있는 라우트"
  echo "----------------------------------------------------------------"
  printf '%b\n' "$missing_report"
  echo
  echo "  의도된 차이라면 해당 yml 에 이유를 주석으로 남기고, 이 스크립트의"
  echo "  EXPECTED_DIFF 목록에 추가할 것 — 조용히 두지 말 것."
fi

# ── 2) 같은 id 인데 Path 술어가 다른 경우
path_report=""
while read -r id; do
  paths="$(for env in "${ENVS[@]}"; do
    awk -F"\t" -v k="$id" '$1 == k { print $2 }' "$tmp/$env.tsv" 2>/dev/null || true
  done | sort -u)"
  if [ "$(echo "$paths" | grep -c .)" -gt 1 ]; then
    path_report="$path_report\n  $id\n$(echo "$paths" | sed 's/^/      /')"
    drift=1
  fi
done < "$tmp/all-ids"

if [ -n "$path_report" ]; then
  echo
  echo "⚠️  같은 라우트 id 가 환경별로 다른 경로를 가리킨다"
  echo "----------------------------------------------------------------"
  printf '%b\n' "$path_report"
fi

# ── 3) 죽은 라우트: 어느 서비스에도 매핑이 없는 경로
if [ -d "$BACKEND_DIR" ]; then
  dead_report=""
  # 이 모노레포에 소스가 있는 서비스만 판정한다. auth-service·discovery·notify-service 는
  # msa-scaffold 레포 소유라 여기서 매핑을 찾을 수 없고, 없다고 죽은 것이 아니다 (UG-249).
  local_services=""
  for d in "$BACKEND_DIR"/*/; do
    [ -d "$d" ] || continue
    local_services="$local_services $(basename "$d")"
  done

  while IFS=$'\t' read -r id path uri; do
    # uri 가 가리키는 서비스가 이 레포에 없으면 판정 대상에서 제외
    svc="${uri%%-service}"; svc="${svc%%-server}"
    case " $local_services " in
      *" $svc "*) ;;
      *) continue ;;
    esac
    # Path=/api/v1/foo/** → /api/v1/foo 로 줄여 컨트롤러 매핑을 찾는다.
    # 콤마로 여러 경로를 쓰는 라우트는 첫 경로만 본다.
    probe="${path%%,*}"
    probe="${probe%%/\*\**}"
    probe="${probe%/}"
    case "$probe" in
      /api/v1/*) ;;
      *) continue ;;   # /ws/**, /fxp-preprocess-service/** 등은 대상 아님
    esac
    if ! grep -rqF "\"$probe" --include="*.java" "$BACKEND_DIR" 2>/dev/null \
       && ! grep -rqF "= \"$probe" --include="*.java" "$BACKEND_DIR" 2>/dev/null; then
      dead_report="$dead_report\n  $id  ($probe)"
    fi
  done < <(cat "$tmp"/*.tsv | sort -u)

  if [ -n "$dead_report" ]; then
    echo
    echo "⚠️  어느 서비스에도 매핑이 없는 라우트 (죽은 라우트 후보)"
    echo "----------------------------------------------------------------"
    printf '%b\n' "$(echo "$dead_report" | sort -u)"
    echo
    echo "  실제로 죽었는지 확인 후 제거할 것. 다른 레포(msa-scaffold)의 서비스가"
    echo "  처리하는 경로일 수 있으니 단정하지 말 것."
    drift=1
  fi
else
  echo
  echo "  (backend 경로가 없어 죽은 라우트 점검은 건너뜀: $BACKEND_DIR)"
fi

echo
echo "================================================================"
if [ "$drift" -eq 0 ]; then
  echo "  ✅ 드리프트 없음"
else
  echo "  ⚠️  위 항목을 확인할 것"
fi
echo "  환경별 라우트 수: $(for env in "${ENVS[@]}"; do printf '%s=%s ' "$env" "$(wc -l < "$tmp/$env.tsv" | tr -d ' ')"; done)"
echo "================================================================"

exit "$drift"

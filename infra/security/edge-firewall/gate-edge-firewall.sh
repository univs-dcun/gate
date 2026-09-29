#!/usr/bin/env bash
# gate-edge-firewall — 인터넷 쪽 인터페이스에서 내부용 포트를 막는다 (UG-343)
#
# 운영 서버는 공인 IP 가 인터페이스에 직접 붙어 있고 앞단 방화벽이 없다. compose 가 0.0.0.0 에
# 게시한 포트가 전부 인터넷에서 닿았다(DB·Redis·discovery·config 포함). Docker 가 게시한 포트는
# ufw 를 우회하므로 DOCKER-USER 체인에서 막는다. 판정은 DNAT 전의 호스트 포트(--ctorigdstport)다.
#
# 멱등하다 — 이미 있는 규칙은 건너뛴다. 여러 번 실행해도 중복이 생기지 않는다.
# 사내망 인터페이스와 컨테이너 간 통신, nginx → 127.0.0.1 프록시는 영향이 없다.
set -euo pipefail

EXT_IF="${EXT_IF:-ens9f3}"                          # 인터넷 쪽 인터페이스
DOCKER_HOST_PORTS=(7432 7379 7761 7888 7947 7080 7750 7751)   # compose 가 게시한 호스트 포트
HOST_PORTS=(22)                                     # 호스트 서비스(SSH)
IPT="${IPT:-iptables}"
WAIT_SECS="${WAIT_SECS:-120}"

log() { echo "gate-edge-firewall: $*"; }

ensure_rule() {
  local chain=$1; shift
  if "$IPT" -C "$chain" "$@" 2>/dev/null; then
    log "present: $chain $*"
  else
    "$IPT" -I "$chain" "$@"
    log "added:   $chain $*"
  fi
}

# 인터페이스 이름이 바뀌었는데 조용히 성공하면 아무것도 막지 않은 채 "정상" 이 된다 — 실패로 끝낸다.
if ! ip link show "$EXT_IF" >/dev/null 2>&1; then
  log "interface $EXT_IF not found — nothing applied"
  exit 1
fi

# SSH 는 Docker 와 무관하므로 먼저 건다.
for p in "${HOST_PORTS[@]}"; do
  ensure_rule INPUT -i "$EXT_IF" -p tcp --dport "$p" -j DROP
done

# DOCKER-USER 는 dockerd 가 만든다. 부팅 직후에는 아직 없을 수 있어 기다린다.
for ((i = 0; i < WAIT_SECS; i++)); do
  "$IPT" -nL DOCKER-USER >/dev/null 2>&1 && break
  sleep 1
done
if ! "$IPT" -nL DOCKER-USER >/dev/null 2>&1; then
  log "DOCKER-USER chain not found after ${WAIT_SECS}s — container ports NOT protected"
  exit 1
fi

for p in "${DOCKER_HOST_PORTS[@]}"; do
  ensure_rule DOCKER-USER -i "$EXT_IF" -p tcp -m conntrack --ctorigdstport "$p" -j DROP
done

log "done (${#HOST_PORTS[@]} host + ${#DOCKER_HOST_PORTS[@]} container ports on $EXT_IF)"

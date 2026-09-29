#!/bin/sh
set -e

API_BASE_URL="${UNIVS_API_BASE_URL:-http://gateway-server:8080/api}"
API_GUIDE_URL="${UNIVS_API_GUIDE_URL:-}"
MOBILE_BASE_URL="${UNIVS_MOBILE_BASE_URL:-}"
QR_URL_REWRITE="${QR_URL_REWRITE:-false}"

# UG-342: 이메일 인증(가입·비밀번호 찾기)을 쓰는 설치인가. 온프레미스(메일 없음)는 false 를 준다 —
# gate-web 이 두 진입점을 숨기고 경로를 로그인으로 돌린다. 기본값 true 는 클라우드 동작 그대로다.
# JS 불리언 리터럴로 내보내야 한다: "false"(문자열)는 !== false 라 켜진 채로 남는다. 그래서 값을 검사한다.
EMAIL_AUTH_ENABLED=$(printf '%s' "${UNIVS_EMAIL_AUTH_ENABLED:-true}" | tr '[:upper:]' '[:lower:]')
case "$EMAIL_AUTH_ENABLED" in
  true|false) ;;
  *) echo "[gate-web] UNIVS_EMAIL_AUTH_ENABLED must be true or false (got: $EMAIL_AUTH_ENABLED)" >&2; exit 1 ;;
esac

cat > /usr/share/nginx/html/config.js <<EOF
window.__APP_CONFIG__ = {
  apiBaseUrl: "${API_BASE_URL}",
  qrUrlRewrite: ${QR_URL_REWRITE},
  apiGuideUrl: "${API_GUIDE_URL}",
  mobileBaseUrl: "${MOBILE_BASE_URL}",
  emailAuthEnabled: ${EMAIL_AUTH_ENABLED},
};
EOF

exec nginx -g "daemon off;"

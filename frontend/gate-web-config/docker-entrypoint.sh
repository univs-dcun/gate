#!/bin/sh
set -e

API_BASE_URL="${UNIVS_API_BASE_URL:-http://gateway-server:8080/api}"
API_GUIDE_URL="${UNIVS_API_GUIDE_URL:-}"
MOBILE_BASE_URL="${UNIVS_MOBILE_BASE_URL:-}"
QR_URL_REWRITE="${QR_URL_REWRITE:-false}"

cat > /usr/share/nginx/html/config.js <<EOF
window.__APP_CONFIG__ = {
  apiBaseUrl: "${API_BASE_URL}",
  qrUrlRewrite: ${QR_URL_REWRITE},
  apiGuideUrl: "${API_GUIDE_URL}",
  mobileBaseUrl: "${MOBILE_BASE_URL}",
};
EOF

exec nginx -g "daemon off;"

#!/bin/sh
set -e

BACKEND_URL="${UNIVS_API_BASE_URL:-https://develop.univs.ai:18090}"

cat > /app/public/config.js <<EOF
window.__UNIVS_CONFIG__ = {
  UNIVS_API_BASE_URL: "${BACKEND_URL}",
};
EOF

exec node server-https.js

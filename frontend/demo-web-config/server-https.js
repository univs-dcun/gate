const https = require('https');
const http = require('http');
const fs = require('fs');

const certPath = process.env.SSL_CERT_PATH;
const keyPath = process.env.SSL_KEY_PATH;

if (certPath && keyPath && fs.existsSync(certPath) && fs.existsSync(keyPath)) {
  const cert = fs.readFileSync(certPath);
  const key = fs.readFileSync(keyPath);

  const originalCreateServer = http.createServer;
  http.createServer = function (options, handler) {
    if (typeof options === 'function') {
      handler = options;
      options = {};
    }
    return https.createServer({ ...options, cert, key }, handler);
  };

  console.log(`[demo-web] HTTPS enabled (cert: ${certPath})`);
} else {
  console.log('[demo-web] SSL_CERT_PATH or SSL_KEY_PATH not set — starting HTTP');
}

require('./server.js');

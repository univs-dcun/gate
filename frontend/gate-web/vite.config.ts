import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],
  server: {
    host: true,
    // 모든 호스트 허용 (Vite host 차단 비활성화) — dev 전용
    allowedHosts: true,
    // HTTPS 리버스 프록시(dev.univsgate.com → localhost:5173) 뒤에서 HMR WebSocket 연결.
    // 브라우저가 페이지와 같은 호스트의 443 포트로 wss 접속하도록 강제(프록시가 WS 업그레이드 전달 필요).
    hmr: {
      clientPort: 443,
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    // 프로덕션 빌드 타겟 — 최신 브라우저 대상
    target: 'esnext',

    // terser — esbuild보다 약간 더 작은 번들 생성 (느리지만 최적화 강도 높음)
    minify: 'terser',

    // 소스맵 비활성화 (배포 시 코드 노출 방지, 번들 크기 감소)
    sourcemap: false,

    // recharts + d3 의존성은 구조상 큰 청크가 불가피 → 임계값 상향
    chunkSizeWarningLimit: 700,

    // 벤더 라이브러리를 별도 청크로 분리 → 캐싱 효율 향상
    rollupOptions: {
      output: {
        manualChunks: {
          // React 코어 — 거의 변경되지 않으므로 별도 캐싱
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          // 차트 라이브러리 — d3 의존성 포함으로 용량이 크므로 분리
          'charts': ['recharts'],
          // 서버 상태 관리
          'query': ['@tanstack/react-query'],
          // 다국어 처리
          'i18n': ['i18next', 'react-i18next'],
          // HTTP 클라이언트
          'http': ['axios'],
        },
      },
    },
  },
})

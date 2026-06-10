import { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import QRCode from 'qrcode';

/* ── 아이콘 ── */
const ChevronRightIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <polyline points="9 18 15 12 9 6" />
  </svg>
);

const CloseIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
  </svg>
);

const CopyIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
  </svg>
);

const CheckIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#0fb981"
    strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <polyline points="20 6 9 17 4 12" />
  </svg>
);

/* ── QR 캔버스 ── */

/* ── 사이드 패널 ── */
function DemoQRPanel({ url, onClose }: { url: string; onClose: () => void }) {
  const { t } = useTranslation();
  const [copied, setCopied] = useState(false);
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    if (canvasRef.current) {
      QRCode.toCanvas(canvasRef.current, url, { width: 300, margin: 1, color: { dark: '#475569', light: '#ffffff' } });
    }
  }, [url]);

  const handleCopy = () => {
    navigator.clipboard.writeText(url).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  return (
    <div
      className="fixed inset-0 z-[var(--z-modal)] flex items-center justify-end bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]"
      onClick={onClose}
    >
      <div
        className="relative h-full flex items-center p-[26px]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="bg-white flex flex-col h-full w-[500px] px-9 py-10 rounded-[34px] shadow-[0_8px_40px_0_rgba(0,0,0,0.12)]">
          {/* 타이틀 + 닫기 */}
          <div className="flex items-center justify-between mb-6 flex-shrink-0">
            <h2 className="text-[24px] font-semibold text-[#334155] tracking-[-0.6px] leading-[40px]">
              {t('dashboard.demo_qr_title')}
            </h2>
            <button
              onClick={onClose}
              className="w-9 h-9 flex items-center justify-center rounded-full hover:bg-[#f1f5f9] transition-colors text-[#94a3b8]"
            >
              <CloseIcon />
            </button>
          </div>

          {/* QR 영역 */}
          <div className="flex-1 flex flex-col justify-between min-h-0">
            <div className="bg-[#f9fafc] rounded-[16px] px-5 py-6 flex flex-col items-center gap-5">
              <p className="text-[18px] font-semibold text-[#006fff] tracking-[-0.45px] leading-7 text-center">
                {t('dashboard.demo_qr_scan_hint')}
              </p>
              <canvas ref={canvasRef} className="w-[300px] h-[300px]" />
              <p className="text-[15px] text-[#475569] tracking-[-0.375px] leading-6 text-center underline break-all">
                {url}
              </p>
              <button
                onClick={handleCopy}
                className="w-full flex items-center justify-center gap-2 px-3 py-3 bg-white border border-[#cbd5e1] rounded-[8px] text-[14px] text-[#334155] tracking-[-0.35px] hover:bg-[#f8fafc] transition-colors"
              >
                {copied ? <CheckIcon /> : <CopyIcon />}
                {copied ? t('dashboard.demo_qr_copied') : t('dashboard.demo_qr_copy')}
              </button>
            </div>

            <button
              onClick={onClose}
              className="mt-5 w-full h-12 flex items-center justify-center bg-[#f1f5f9] rounded-[8px] text-[18px] font-semibold text-[#475569] tracking-[-0.45px] hover:bg-[#e2e8f0] transition-colors flex-shrink-0"
            >
              {t('common.close')}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ── 카드 ── */
export default function DemoQRCard({ apiKey }: { apiKey: string }) {
  const { t } = useTranslation();
  const [panelOpen, setPanelOpen] = useState(false);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const mobileBase = window.__APP_CONFIG__?.mobileBaseUrl ?? import.meta.env.VITE_MOBILE_BASE_URL ?? 'https://develop.univs.ai:7778';
  const demoUrl = `${mobileBase}?apiKey=${apiKey}`;

  useEffect(() => {
    if (canvasRef.current) {
      QRCode.toCanvas(canvasRef.current, demoUrl, { width: 64, margin: 1, color: { dark: '#475569', light: '#ffffff' } });
    }
  }, [demoUrl]);

  return (
    <>
      {/* Figma node 1720:2013 (QRBannerBig) — 파란 테두리 컴팩트 카드 */}
      <button
        type="button"
        onClick={() => setPanelOpen(true)}
        className="w-full h-full text-left flex items-center gap-1 bg-white border border-[var(--color-primary-400)] rounded-[12px] p-4 hover:bg-[var(--color-primary-100)] transition-colors"
      >
        <div className="flex-1 min-w-0 flex flex-col gap-2 justify-center">
          {/* 헤더: DEMO 뱃지 + 데모 QR + chevron */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="inline-flex items-center justify-center h-[18px] px-1 rounded-[4px] bg-[var(--color-primary-400)] text-white text-[12px] font-semibold tracking-[-0.3px] leading-[14px]">
                DEMO
              </span>
              <span className="text-[14px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.35px] leading-[20px] whitespace-nowrap">
                {t('dashboard.demo_qr_card_title')}
              </span>
            </div>
            <span className="text-[var(--color-neutral-400)]">
              <ChevronRightIcon />
            </span>
          </div>

          {/* 본문: QR + 안내 텍스트 */}
          <div className="flex items-center gap-2">
            <span className="flex items-center justify-center bg-white rounded-[8px] flex-shrink-0">
              {/* 렌더 크기(64px) = 표시 크기. CSS 축소에 의존하지 않음 */}
              <canvas ref={canvasRef} className="block" />
            </span>
            <span className="flex flex-col gap-1 flex-1 min-w-0">
              <span className="text-[14px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.35px] leading-[20px]">
                {t('dashboard.demo_qr_scan_action')}
              </span>
              <span className="text-[13px] font-normal text-[var(--color-neutral-500)] tracking-[-0.325px] leading-[17px] line-clamp-3">
                {t('dashboard.demo_qr_scan_desc')}
              </span>
            </span>
          </div>
        </div>
      </button>

      {panelOpen && (
        <DemoQRPanel url={demoUrl} onClose={() => setPanelOpen(false)} />
      )}
    </>
  );
}

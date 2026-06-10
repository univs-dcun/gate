import { useTranslation } from 'react-i18next';
import { MobileLayout } from '@/components/layout';

/* ─── 아이콘 ─────────────────────────────────────────────── */
const WifiOffIcon = () => (
  <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <line x1="2" y1="2" x2="22" y2="22" />
    <path d="M8.5 16.5a5 5 0 0 1 7 0" />
    <path d="M2 8.82a15 15 0 0 1 4.17-2.65" />
    <path d="M10.66 5c4.01-.36 8.14.9 11.34 3.76" />
    <path d="M16.85 11.25a10 10 0 0 1 2.22 1.68" />
    <path d="M5 12.85a10 10 0 0 1 5.17-2.8" />
    <circle cx="12" cy="20" r="1" fill="currentColor" stroke="none" />
  </svg>
);

/* ─── 페이지 ─────────────────────────────────────────────── */
export default function MobileNetworkErrorPage() {
  const { t } = useTranslation();

  return (
    <MobileLayout>
      <div className="flex-1 flex flex-col items-center justify-center px-6 gap-6">
        {/* 아이콘 */}
        <div className="w-20 h-20 rounded-full bg-[#fee2e2] flex items-center justify-center text-[#ef4444]">
          <WifiOffIcon />
        </div>

        {/* 텍스트 */}
        <div className="flex flex-col items-center gap-2 text-center">
          <h1 className="text-[20px] font-semibold text-[#1e293b] tracking-[-0.5px]">
            {t('network_error.title')}
          </h1>
          <p className="text-[14px] text-[#64748b] leading-relaxed tracking-[-0.35px] whitespace-pre-line">
            {t('network_error.description')}
          </p>
        </div>

        {/* 관리자 문의 */}
        <div className="w-full bg-[#f5f6f8] rounded-xl px-5 py-4 flex flex-col gap-1">
          <p className="text-[12px] font-semibold text-[#475569]">{t('network_error.contact_label')}</p>
          <p className="text-[14px] text-[#006fff] font-medium">{t('network_error.contact_email')}</p>
        </div>

        {/* 다시 시도 버튼 */}
        <button
          onClick={() => window.location.reload()}
          className="w-full h-[52px] bg-[#006fff] text-white rounded-2xl text-[18px] font-semibold tracking-[-0.45px] transition-colors hover:bg-[#0059d4] active:bg-[#0047aa]"
        >
          {t('network_error.retry')}
        </button>
      </div>
    </MobileLayout>
  );
}

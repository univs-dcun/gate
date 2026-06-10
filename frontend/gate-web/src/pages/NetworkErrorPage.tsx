import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui';

/* ─── 아이콘 ─────────────────────────────────────────────── */
const WifiOffIcon = () => (
  <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
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
export default function NetworkErrorPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-[var(--color-gray-bg)] flex items-center justify-center p-6">
      <div className="bg-white rounded-2xl shadow-sm border border-[var(--color-neutral-200)] max-w-md w-full px-10 py-12 flex flex-col items-center text-center gap-6">

        {/* 아이콘 */}
        <div className="w-20 h-20 rounded-full bg-[var(--color-entry-bg)] flex items-center justify-center text-[var(--color-entry)]">
          <WifiOffIcon />
        </div>

        {/* 텍스트 */}
        <div className="flex flex-col gap-2">
          <h1 className="text-xl font-semibold text-[var(--color-neutral-900)]">
            {t('network_error.title')}
          </h1>
          <p className="text-sm text-[var(--color-neutral-500)] leading-relaxed whitespace-pre-line">
            {t('network_error.description')}
          </p>
        </div>

        {/* 관리자 문의 안내 */}
        <div className="w-full bg-[var(--color-gray-bg)] rounded-xl px-5 py-4 text-left flex flex-col gap-1">
          <p className="text-xs font-semibold text-[var(--color-neutral-700)]">
            {t('network_error.contact_label')}
          </p>
          <p className="text-sm text-[var(--color-link-blue)] font-medium">
            {t('network_error.contact_email')}
          </p>
        </div>

        {/* 버튼 */}
        <div className="flex flex-col gap-3 w-full">
          <Button
            variant="primary"
            size="md"
            className="w-full"
            onClick={() => window.location.reload()}
          >
            {t('network_error.retry')}
          </Button>
          <Button
            variant="ghost"
            size="md"
            className="w-full"
            onClick={() => navigate('/login')}
          >
            {t('network_error.go_login')}
          </Button>
        </div>
      </div>
    </div>
  );
}

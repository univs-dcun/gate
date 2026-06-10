import { useTranslation } from 'react-i18next';

/**
 * ConsentBadge — 개인정보 표시 동의 여부 뱃지 (상단 GNB용)
 *
 * Figma node 1712:10503 (Bedge) 기반
 *  - consented=true  (동의함): warning/400 배경 + 흰 텍스트 + filled shield
 *  - consented=false (미동의): neutral/100 배경 + neutral/600 텍스트 + outline shield
 *
 * 공통: px-8 py-6, rounded-8, gap-4, 아이콘 16px, SemiBold 14px, tracking -0.35px
 */

interface ConsentBadgeProps {
  consented: boolean;
  className?: string;
}

/* ── 방패 아이콘 (filled: 동의함 / outline: 미동의) ── */
function ShieldIcon({ filled }: { filled: boolean }) {
  return filled ? (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <path
        d="M8 1.5l5 1.8v4.2c0 3.1-2.1 5.4-5 6.5-2.9-1.1-5-3.4-5-6.5V3.3L8 1.5z"
        fill="currentColor"
      />
      <path
        d="M5.6 8.1l1.7 1.7 3.1-3.4"
        stroke="#fff"
        strokeWidth="1.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  ) : (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <path
        d="M8 1.9l4.4 1.6v3.9c0 2.8-1.9 4.9-4.4 5.9-2.5-1-4.4-3.1-4.4-5.9V3.5L8 1.9z"
        stroke="currentColor"
        strokeWidth="1.3"
        strokeLinejoin="round"
      />
      <path
        d="M5.8 8l1.5 1.5 2.9-3.1"
        stroke="currentColor"
        strokeWidth="1.3"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

const WRAPPER_BASE = 'inline-flex items-center gap-1 px-2 py-1.5 rounded-[8px] whitespace-nowrap';
const TEXT_BASE = 'font-semibold text-[14px] leading-[20px] tracking-[-0.35px]';

const VARIANT: Record<'on' | 'off', string> = {
  on:  'bg-[var(--color-learning)] text-white',
  off: 'bg-[var(--color-neutral-100)] text-[var(--color-neutral-600)]',
};

function ConsentBadge({ consented, className }: ConsentBadgeProps) {
  const { t } = useTranslation();
  return (
    <span className={[WRAPPER_BASE, VARIANT[consented ? 'on' : 'off'], className ?? ''].join(' ')}>
      <ShieldIcon filled={consented} />
      <span className={TEXT_BASE}>
        {consented ? t('gnb.consent_on') : t('gnb.consent_off')}
      </span>
    </span>
  );
}

export default ConsentBadge;

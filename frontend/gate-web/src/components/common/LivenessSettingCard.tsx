import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';

/**
 * LivenessSettingCard — 현재 라이브니스 설정 상태 카드 (신규 pill-row 스타일)
 *
 * Figma node 1728:4556 (livenesssetting) 기반.
 * 기존 LivenessStatusCard(2x2 그리드)와 별개의 신규 디자인 — 모듈 행을 세로 리스트로 표시.
 *  - 모듈별 행: ON → success 틴트, OFF → neutral 틴트
 *  - 모듈 목록은 prop으로 주입 (얼굴: 4개 / 손바닥: 2개 등 가변)
 */

export interface LivenessModule {
  /** 표시 라벨 (이미 번역된 문자열) */
  label: string;
  enabled: boolean;
}

interface LivenessSettingCardProps {
  modules: LivenessModule[];
  /** '변경' 클릭 시 이동 경로 (기본: 설정 페이지) */
  changeHref?: string;
  className?: string;
}

/* ── ON/OFF 상태 칩 (모듈 토글칩) ── */
function ModuleStatusChip({ enabled }: { enabled: boolean }) {
  return (
    <span
      className={[
        'inline-flex items-center justify-center gap-1 px-2 rounded-[4px] h-[22px] flex-shrink-0',
        enabled
          ? 'bg-[var(--color-success-400)] text-white w-[60px]'
          : 'bg-[var(--color-neutral-200)] text-[var(--color-neutral-800)]',
      ].join(' ')}
    >
      {enabled ? <EyeIcon /> : <EyeOffIcon />}
      <span className="text-[13px] font-normal tracking-[-0.325px] leading-[18px]">
        {enabled ? 'ON' : 'OFF'}
      </span>
    </span>
  );
}

const EyeIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M2 12s4-6.5 10-6.5S22 12 22 12s-4 6.5-10 6.5S2 12 2 12Z" />
    <circle cx="12" cy="12" r="2.5" />
  </svg>
);
const EyeOffIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M2 12s4-6.5 10-6.5S22 12 22 12s-4 6.5-10 6.5S2 12 2 12Z" />
    <circle cx="12" cy="12" r="2.5" />
    <line x1="5" y1="5" x2="19" y2="19" />
  </svg>
);

export default function LivenessSettingCard({ modules, changeHref = '/dashboard/settings', className }: LivenessSettingCardProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();

  return (
    <div className={['flex flex-col gap-[18px] p-4 bg-white border border-[var(--color-neutral-300)] rounded-[12px]', className ?? ''].join(' ')}>
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <span className="text-[15px] font-semibold text-[var(--color-neutral-700)] leading-[20px] whitespace-nowrap">
          {t('dashboard.liveness_status_title')}
        </span>
        <button
          onClick={() => navigate(changeHref)}
          className="flex items-center gap-0.5 text-[14px] font-normal text-[var(--color-neutral-500)] tracking-[-0.35px] hover:text-[var(--color-neutral-700)] transition-colors whitespace-nowrap flex-shrink-0"
        >
          {t('common.change')}
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="9 18 15 12 9 6" />
          </svg>
        </button>
      </div>

      {/* 모듈 상태 행 리스트 */}
      <div className="flex flex-col gap-2">
        {modules.map((m, i) => (
          <div
            key={`${m.label}-${i}`}
            className={[
              'flex items-center justify-between px-3 py-3 rounded-[8px]',
              m.enabled ? 'bg-[var(--color-success-100)]' : 'bg-[var(--color-neutral-50-bg)]',
            ].join(' ')}
          >
            <span
              className={[
                'text-[14px] font-semibold tracking-[-0.35px] leading-[20px] whitespace-nowrap',
                m.enabled ? 'text-[var(--color-success-700)]' : 'text-[var(--color-neutral-700)]',
              ].join(' ')}
            >
              {m.label}
            </span>
            <ModuleStatusChip enabled={m.enabled} />
          </div>
        ))}
      </div>
    </div>
  );
}

import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';

interface LivenessStatusCardProps {
  livenessRegisterEnabled:        boolean;
  livenessVerifyingByIdEnabled:    boolean;
  livenessVerifyingByImageEnabled: boolean;
  livenessIdentifyingEnabled:      boolean;
}

/* OFF — ON 아이콘(눈) + X 오버레이, 빨간색 */
const OffIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#ef4444"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M2 12s4-6.5 10-6.5S22 12 22 12s-4 6.5-10 6.5S2 12 2 12Z" />
    <circle cx="12" cy="12" r="2.5" />
    <line x1="9" y1="9" x2="15" y2="15" />
    <line x1="15" y1="9" x2="9" y2="15" />
  </svg>
);

/* ON — 초록 눈(라이브니스) 아이콘 */
const OnIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#0fb981"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M2 12s4-6.5 10-6.5S22 12 22 12s-4 6.5-10 6.5S2 12 2 12Z" />
    <circle cx="12" cy="12" r="2.5" />
  </svg>
);

/* 각 항목: 라벨 → 아이콘 → ON/OFF */
function StatusRow({ label, enabled, align = 'start' }: { label: string; enabled: boolean; align?: 'start' | 'end' }) {
  const statusBadge = (
    <div className="flex items-center gap-0.5">
      <span className="w-7 flex items-center justify-center flex-shrink-0">
        {enabled ? <OnIcon /> : <OffIcon />}
      </span>
      <span className={['w-7 text-center text-[12px] font-semibold tracking-[-0.3px] whitespace-nowrap', enabled ? 'text-[#0fb981]' : 'text-[#ef4444]'].join(' ')}>
        {enabled ? 'ON' : 'OFF'}
      </span>
    </div>
  );

  if (align === 'end') {
    return (
      <div className="flex items-center justify-end h-full">
        <span className="w-[80px] flex-shrink-0 mr-2 text-[13px] font-medium text-[#334155] tracking-[-0.325px] whitespace-nowrap">
          {label}
        </span>
        {statusBadge}
      </div>
    );
  }

  return (
    <div className="flex items-center h-full">
      <span className="w-[65px] flex-shrink-0 mr-2 text-[13px] font-medium text-[#334155] tracking-[-0.325px] whitespace-nowrap">
        {label}
      </span>
      {statusBadge}
    </div>
  );
}

export default function LivenessStatusCard({
  livenessRegisterEnabled,
  livenessVerifyingByIdEnabled,
  livenessVerifyingByImageEnabled,
  livenessIdentifyingEnabled,
}: LivenessStatusCardProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();

  return (
    <div className="flex flex-col p-4 bg-white border border-[#CBD5E1] rounded-[12px] shadow-[0px_2px_6px_0px_rgba(0,0,0,0.02)] h-full gap-3">
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <span className="text-[16px] font-medium text-[#334155] tracking-[-0.4px] whitespace-nowrap">
          {t('dashboard.liveness_status_title')}
        </span>
        <button
          onClick={() => navigate('/dashboard/settings')}
          className="flex items-center gap-0.5 text-[13px] font-medium text-[#006fff] tracking-[-0.325px] hover:opacity-80 transition-opacity whitespace-nowrap flex-shrink-0"
        >
          {t('common.change')}
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="9 18 15 12 9 6" />
          </svg>
        </button>
      </div>

      {/* 상태 그리드 2x2 */}
      <div className="w-full h-[1px]" />
      <div className="grid grid-cols-2 grid-rows-2 gap-y-0 gap-x-0 flex-1">
        <StatusRow label={t('module.enrollment')}   enabled={livenessRegisterEnabled}         align="start" />
        <StatusRow label={t('module.verification')} enabled={livenessVerifyingByIdEnabled}    align="end" />
        <StatusRow label={t('module.matching')}     enabled={livenessIdentifyingEnabled}      align="start" />
        <StatusRow label={t('module.verify_image')} enabled={livenessVerifyingByImageEnabled} align="end" />
      </div>
    </div>
  );
}

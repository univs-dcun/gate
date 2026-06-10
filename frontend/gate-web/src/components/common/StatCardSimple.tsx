import { useTranslation } from 'react-i18next';

interface StatCardSimpleProps {
  title:      string;
  value:      string;  // 기간 내 건수 (periodCount)
  totalValue?: string; // 전체 누적 건수 (totalCount)
}

const HelpIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#94a3b8"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <circle cx="12" cy="12" r="10" />
    <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
    <line x1="12" y1="17" x2="12.01" y2="17" strokeWidth="2.5" />
  </svg>
);

function StatCardSimple({ title, value, totalValue }: StatCardSimpleProps) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col p-4 bg-white border border-[#CBD5E1] rounded-[12px] shadow-[0px_2px_6px_0px_rgba(0,0,0,0.02)] h-full gap-3">
      {/* 타이틀 + ? 아이콘 */}
      <div className="flex items-center gap-1.5">
        <span className="text-[16px] font-medium text-[#334155] tracking-[-0.4px] leading-6 whitespace-nowrap">
          {title}
        </span>
        <HelpIcon />
      </div>

      {/* 기간 내 건수 + 단위 */}
      <div className="flex items-baseline gap-1 flex-1">
        <span className="text-[32px] font-semibold text-[#1e293b] tracking-[-0.8px] leading-none">
          {value}
        </span>
        <span className="text-[16px] font-medium text-[#475569] tracking-[-0.4px] leading-none">
          {t('common.count_unit')}
        </span>
      </div>

      {/* 전체 누적 건수 — 구분선 + Total 레이블 */}
      {totalValue !== undefined && (
        <>
          <div className="border-t border-[#e2e8f0]" />
          <div className="flex items-center justify-between">
            <span className="text-[13px] font-medium text-[#94a3b8] tracking-[-0.325px]">
              {t('common.total')}
            </span>
            <span className="inline-flex items-baseline gap-1">
              <span className="text-[14px] font-semibold text-[#475569] tracking-[-0.35px]">{totalValue}</span>
              <span className="text-[13px] font-normal text-[#64748b] tracking-[-0.325px]">{t('common.count_unit')}</span>
            </span>
          </div>
        </>
      )}
    </div>
  );
}

export default StatCardSimple;

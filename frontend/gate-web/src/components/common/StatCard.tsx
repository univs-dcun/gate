import { useTranslation } from 'react-i18next';
import { CircularProgress } from '@/components/ui';

type StatColor = 'entry' | 'task' | 'learning' | 'blue' | 'purple';

interface Delta {
  value: string;       // e.g. "24건"
  direction: 'up' | 'down';
}

interface StatCardProps {
  title: string;
  value: string;
  unit?: string;
  remaining: string;
  percentage: number;
  color: StatColor;
  warning?: string;        // 경고 메시지 (e.g. "사용량 10% 남았어요!")
  delta?: Delta;           // 변화량 칩 (e.g. { value: '24건', direction: 'down' })
  livenessEnabled?: boolean; // 라이브니스 적용 상태 (undefined = 미표시)
}


/* 위쪽을 가리키는 화살표 SVG — direction='down' 시 -scale-y-100 으로 뒤집음 */
const ArrowUpIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <line x1="12" y1="19" x2="12" y2="5" />
    <polyline points="5 12 12 5 19 12" />
  </svg>
);

/**
 * Figma 대시보드 기반 지표 카드
 *
 * - delta: 변화량 칩 (n건 + 방향 화살표)
 * - warning: 말풍선 경고 배지 (링 위에 오버레이)
 */
function StatCard({ title, value, unit, remaining, percentage, color: _color, warning, delta, livenessEnabled }: StatCardProps) {
  const { t } = useTranslation();
  const displayUnit = unit ?? t('common.count_unit');
  // Figma: 임계치 이상 → --error-fg #D83232, 일반 → --neutral_800 #1E293B
  const ringColor = warning ? '#D83232' : '#1E293B';

  return (
    <div
      className="relative flex items-start justify-between p-3 h-[140px]
                 border border-[var(--card-border)] rounded-[var(--card-radius)] shadow-[var(--card-shadow)]"
      style={{
        backgroundImage: 'linear-gradient(284deg, #F5F6F8 4.58%, rgba(255, 255, 255, 0.00) 60.73%)',

        // backgroundImage: 'linear-gradient(287deg, #F5F6F8 14.02%, rgba(255, 255, 255, 0.00) 82.28%)',
        // boxShadow:'0 2px 6px 0 rgba(0, 0, 0, 0.02)',
      }}
    >
      {/* 좌측: 수치 정보 */}
      <div className="flex flex-col gap-4">
        <div className="flex items-center gap-2">
          <p className="text-[length:var(--text-base)] font-medium text-[var(--color-neutral-700)] tracking-[-0.4px] leading-[var(--leading-normal)]">
            {title}
          </p>
          {livenessEnabled !== undefined && (
            livenessEnabled ? (
              <span className="inline-flex items-center gap-1 px-3 py-1 line-height:normal rounded-full bg-[var(--color-task-bg)] border border-[var(--color-task)] text-[var(--color-liveness-on-fg)] text-[11px] font-semibold tracking-[-0.2px] whitespace-nowrap">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
                </svg>
                {t('module.liveness_on')}
              </span>
            ) : (
              <span className="inline-flex items-center px-3 py-1 line-height:normal rounded-full bg-[var(--color-neutral-100)] border border-[var(--color-neutral-300)] text-[var(--color-neutral-600)] text-[11px] font-semibold tracking-[-0.2px] whitespace-nowrap">
                
                {t('module.liveness_off')}
              </span>
            )
          )}
        </div>

        <div className="flex flex-col gap-0">
          {/* 값 + delta 칩 */}
          <div className="flex items-end gap-2 fon">
            <h3 className="text-[var(--color-neutral-800)] leading-[var(--leading-normal)] tracking-[-0.7px]">
              <span className="text-[26px] font-semibold">{value}</span>
              <span className="ml-1 text-[1rem] font-semibold">{displayUnit}</span>
            </h3>

            {/* 변화량 칩 */}
            {delta && (
              <div className="flex items-center gap-0.5 bg-[var(--color-neutral-100)] rounded-[var(--radius-xs)] pl-2 pr-1 py-1 mb-0.5">
                <span className="text-xs font-semibold text-[var(--color-neutral-700)] tracking-[-0.35px] whitespace-nowrap">
                  {delta.value} 
                </span>
                <span className={delta.direction === 'down' ? '-scale-y-100 inline-flex text-[var(--color-neutral-500)]' : 'inline-flex text-[var(--color-neutral-500)]'}>
                  <ArrowUpIcon />
                </span>
              </div>
            )}
          </div>

          <p className="text-[length:var(--text-base)] font-medium text-[var(--color-neutral-600)] tracking-[-0.4px] leading-[var(--leading-normal)]">
            {remaining}
          </p>
        </div>
      </div>

      {/* 우측: 원형 프로그레스 + 말풍선 경고 배지 */}
      <div className="relative flex-shrink-0 flex flex-col items-center">
        {warning && (
          <div className="absolute -top-3 left-1/2 -translate-x-1/2 flex flex-col items-center z-10">
            {/* 말풍선 본체 */}
            <p
              className="px-2.5 py-0.5 rounded-full text-[length:var(--text-xxs)] font-regular whitespace-nowrap
                         bg-[var(--color-entry)] text-white"
            >
              {warning}
            </p>
            {/* 말풍선 꼬리 — 아래 방향 삼각형 */}
            <div
              style={{
                width: 0,
                height: 0,
                borderLeft: '4px solid transparent',
                borderRight: '4px solid transparent',
                borderTop: '5px solid var(--color-entry)',
              }}
            />
          </div>
        )}
        <div className="mx-3 mt-4" />
        <CircularProgress
          percentage={percentage}
          size={64}
          strokeWidth={5}
          color={ringColor}
          labelColor={ringColor}
        />
      </div>
    </div>
  );
}

export default StatCard;

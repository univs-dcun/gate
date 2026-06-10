import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { DashboardLayout } from '@/components/layout';
import { StatCardSimple, DemoQRCard, LivenessSettingCard, UsageTrendChart, SuccessRateChart, DataTable } from '@/components/common';
import type { LivenessModule } from '@/components/common';
import { AuthMethodTabs } from '@/components/ui';
import type { AuthMethod } from '@/components/ui';
import { getDashboardSummary } from '@/services/dashboard';
import type { DashFeatureType } from '@/services/dashboard';
import type { TrendPeriod } from '@/services/dashboard';
import { getProjectSettings, isLivenessEnabled } from '@/services/project';
import { useProjectContext } from '@/contexts/ProjectContext';

/* ── 기간별 날짜 범위 계산 ── */
function getDateRange(period: TrendPeriod): { start: string; end: string } {
  const end   = new Date();
  const start = new Date();
  if (period === 'WEEK')  start.setDate(end.getDate() - 7);
  if (period === 'MONTH') start.setDate(end.getDate() - 30);
  if (period === 'YEAR')  start.setFullYear(end.getFullYear() - 1);
  const fmt = (d: Date) =>
    `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`;
  return { start: fmt(start), end: fmt(end) };
}

const PERIOD_KEYS: Record<TrendPeriod, string> = {
  WEEK:  'dashboard.period_week',
  MONTH: 'dashboard.period_month',
  YEAR:  'dashboard.period_year',
};

/* ── 기간 선택 드롭다운 ── */
function PeriodSelector({
  value,
  onChange,
}: {
  value: TrendPeriod;
  onChange: (p: TrendPeriod) => void;
}) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const PERIODS = (['WEEK', 'MONTH', 'YEAR'] as TrendPeriod[]);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const { start, end } = getDateRange(value);

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen(o => !o)}
        className="flex items-center justify-between gap-2 w-[310px] px-4 py-3 border-b border-[var(--color-neutral-600)] text-[14px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.35px] hover:border-[var(--color-link-blue)] transition-colors"
      >
        <span className="flex items-center gap-2 min-w-0">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#475569" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="flex-shrink-0">
            <rect x="3" y="4" width="18" height="18" rx="2" />
            <line x1="16" y1="2" x2="16" y2="6" />
            <line x1="8" y1="2" x2="8" y2="6" />
            <line x1="3" y1="10" x2="21" y2="10" />
          </svg>
          <span className="whitespace-nowrap">
            {t(PERIOD_KEYS[value])} ({start}~{end})
          </span>
        </span>
        <svg
          width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#475569"
          strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"
          className={['flex-shrink-0 transition-transform duration-150', open ? 'rotate-180' : ''].join(' ')}
        >
          <polyline points="6 9 12 15 18 9" />
        </svg>
      </button>

      {open && (
        <div className="absolute right-0 top-[calc(100%+4px)] z-50 w-[260px] bg-white border border-[#CBD5E1] rounded-[8px] shadow-[0px_8px_24px_0px_rgba(0,0,0,0.1)] overflow-hidden py-1">
          {PERIODS.map(p => {
            const r = getDateRange(p);
            const isSelected = p === value;
            return (
              <button
                key={p}
                onClick={() => { onChange(p); setOpen(false); }}
                className={[
                  'w-full flex items-center justify-between px-4 py-2.5 text-left hover:bg-[#f8fafc] transition-colors',
                  isSelected ? 'text-[#006fff]' : 'text-[#334155]',
                ].join(' ')}
              >
                <span className="text-[14px] font-medium tracking-[-0.35px] whitespace-nowrap">
                  {t(PERIOD_KEYS[p])} ({r.start}~{r.end})
                </span>
                {isSelected && (
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#006fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <polyline points="20 6 9 17 4 12" />
                  </svg>
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

/* ── 대시보드 콘텐츠 ── */
function DashboardContent() {
  const { t } = useTranslation();
  const { selectedProject } = useProjectContext();
  const queryClient = useQueryClient();
  const location    = useLocation();
  const hasApiKey = !!selectedProject?.apiKey;

  const [period, setPeriod] = useState<TrendPeriod>('MONTH');
  /* 인증방식 탭 — 기본 얼굴인증. 'palm'이면 1:1 인증 통계/라이브니스 항목 제외 */
  const [authMethod, setAuthMethod] = useState<AuthMethod>('face');
  const featureType: DashFeatureType = authMethod === 'palm' ? 'PALM' : 'FACE';
  const isPalm = authMethod === 'palm';

  useEffect(() => {
    queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  }, [location.key]); // eslint-disable-line react-hooks/exhaustive-deps

  const { data: summary } = useQuery({
    queryKey:  ['dashboard', 'summary', selectedProject?.id, period, featureType],
    queryFn:   () => getDashboardSummary(period, featureType).then(r => r.data.data),
    enabled:   hasApiKey,
    staleTime: 0,
  });

  const { data: settings } = useQuery({
    queryKey:  ['project-settings', selectedProject?.id],
    queryFn:   () => getProjectSettings(Number(selectedProject!.id)).then(r => r.data.data),
    enabled:   !!selectedProject?.id,
    staleTime: 0,
  });

  function fmt(n?: number) {
    return n !== undefined ? n.toLocaleString('ko-KR') : '-';
  }

  /* 라이브니스 설정 카드 모듈 — 얼굴: 4개 / 손바닥: 등록·1:N 매칭 2개 */
  const livenessModulesAll: LivenessModule[] = [
    { label: t('module.enrollment'),   enabled: isLivenessEnabled(settings, featureType, 'REGISTER') },
    { label: t('module.matching'),     enabled: isLivenessEnabled(settings, featureType, 'IDENTIFY') },
    { label: t('module.verification'), enabled: isLivenessEnabled(settings, featureType, 'VERIFY_ID') },
    { label: t('module.verify_image'), enabled: isLivenessEnabled(settings, featureType, 'VERIFY_IMAGE') },
  ];
  const livenessModules = authMethod === 'palm' ? livenessModulesAll.slice(0, 2) : livenessModulesAll;

  /* 통계 카드 — 손바닥은 1:1 촬영/사진 인증이 없어 제외 (등록·1:N 매칭·라이브니스) */
  const statCards = [
    { key: 'registration',  title: t('dashboard.stat_registration'),  data: summary?.registration },
    ...(isPalm ? [] : [
      { key: 'verifyById',    title: t('dashboard.stat_verify'),       data: summary?.verifyById },
      { key: 'verifyByImage', title: t('dashboard.stat_verify_image'), data: summary?.verifyByImage },
    ]),
    { key: 'identify',      title: t('dashboard.stat_identify'),       data: summary?.identify },
    { key: 'liveness',      title: t('dashboard.stat_liveness'),       data: summary?.liveness },
  ];

  return (
    <div className="flex flex-col gap-5">
      {/* 페이지 타이틀 */}
      <h1 className="text-[26px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.65px] leading-[var(--leading-normal)]">
        {t('dashboard.title')}
      </h1>

      {/* 인증방식 탭 + 기간 선택기 */}
      <div className="flex items-end justify-between">
        <AuthMethodTabs value={authMethod} onChange={setAuthMethod} />
        <PeriodSelector value={period} onChange={setPeriod} />
      </div>

      {/* ── 본문: 2열 그리드 (행별로 좌측 콘텐츠 ↔ 우측 사이드가 같은 높이로 정렬) ── */}
      <div className="grid gap-x-3 gap-y-5" style={{ gridTemplateColumns: 'minmax(0, 1fr) 252px' }}>
        {/* row1 · col1: 통계 카드 — 얼굴 5개 / 손바닥 3개(등록·1:N 매칭·라이브니스) */}
        <div className="grid gap-3" style={{ gridTemplateColumns: `repeat(${statCards.length}, minmax(0, 1fr))` }}>
          {statCards.map((c) => (
            <StatCardSimple key={c.key} title={c.title} value={fmt(c.data?.periodCount)} totalValue={fmt(c.data?.totalCount)} />
          ))}
        </div>

        {/* row1 · col2: DEMO QR (통계 카드 행 높이에 맞춰 stretch) */}
        {selectedProject?.apiKey ? (
          <DemoQRCard apiKey={selectedProject.apiKey} />
        ) : (
          <div className="bg-white border border-[#CBD5E1] rounded-[12px]" />
        )}

        {/* row2 · col1: 차트 */}
        <div className="grid gap-3" style={{ gridTemplateColumns: 'minmax(0, 1fr) 377px' }}>
          <UsageTrendChart period={period} featureType={featureType} />
          <SuccessRateChart period={period} featureType={featureType} />
        </div>

        {/* row2 · col2: 라이브니스 설정 상태 (높이는 콘텐츠대로 — 차트 높이에 맞추지 않음) */}
        <LivenessSettingCard modules={livenessModules} className="self-start" />

        {/* row3 · col1: 일일 데이터 통계 (좌측 폭만 차지) */}
        <DataTable
          period={period}
          featureType={featureType}
          periodLabel={`${t(PERIOD_KEYS[period])} (${getDateRange(period).start}~${getDateRange(period).end})`}
        />
      </div>
    </div>
  );
}

function DashboardPage() {
  return (
    <DashboardLayout>
      <DashboardContent />
    </DashboardLayout>
  );
}

export default DashboardPage;

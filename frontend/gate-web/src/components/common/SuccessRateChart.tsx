import { useState, useRef, useEffect } from 'react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { PieChart, Pie, ResponsiveContainer } from 'recharts';
import { Card } from '@/components/ui';
import { getDashboardRatios } from '@/services/dashboard';
import type { RatioSummary, TrendPeriod, DashFeatureType } from '@/services/dashboard';
import { useProjectContext } from '@/contexts/ProjectContext';

/* ── 슬라이드 설정 ── */
interface SlideConfig {
  titleKey:         string;
  primaryKey:       string;
  secondaryKey:     string;
  primaryRateKey:   string;
  secondaryRateKey: string;
  primaryColor:     string;
}

const SLIDES: SlideConfig[] = [
  {
    titleKey:         'success_rate.title_registration',
    primaryKey:       'success_rate.enrollment',
    secondaryKey:     'success_rate.deletion',
    primaryRateKey:   'success_rate.enrollment_count',
    secondaryRateKey: 'success_rate.deletion_count',
    primaryColor:     '#4BBBFB',
  },
  {
    titleKey:         'success_rate.title_verify',
    primaryKey:       'success_rate.success',
    secondaryKey:     'success_rate.failure',
    primaryRateKey:   'success_rate.success_count',
    secondaryRateKey: 'success_rate.failure_count',
    primaryColor:     '#F59E0B',
  },
  {
    titleKey:         'success_rate.title_verify_image',
    primaryKey:       'success_rate.success',
    secondaryKey:     'success_rate.failure',
    primaryRateKey:   'success_rate.success_count',
    secondaryRateKey: 'success_rate.failure_count',
    primaryColor:     '#06B6D4',
  },
  {
    titleKey:         'success_rate.title_identify',
    primaryKey:       'success_rate.success',
    secondaryKey:     'success_rate.failure',
    primaryRateKey:   'success_rate.success_count',
    secondaryRateKey: 'success_rate.failure_count',
    primaryColor:     '#8A58FF',
  },
  {
    titleKey:         'success_rate.title_liveness',
    primaryKey:       'success_rate.real',
    secondaryKey:     'success_rate.fake',
    primaryRateKey:   'success_rate.real_count',
    secondaryRateKey: 'success_rate.fake_count',
    primaryColor:     '#10B981',
  },
];

type RatioKey = 'registration' | 'verifyById' | 'verifyByImage' | 'identify' | 'liveness';
const RATIO_KEYS: RatioKey[] = ['registration', 'verifyById', 'verifyByImage', 'identify', 'liveness'];

const EMPTY_RATIO: RatioSummary = { primaryPercent: 0, secondaryPercent: 0, primaryCount: 0, secondaryCount: 0 };

/* ── 아이콘 ── */
const ChevronLeftIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <polyline points="15 18 9 12 15 6" />
  </svg>
);
const ChevronRightIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <polyline points="9 18 15 12 9 6" />
  </svg>
);

/* ── 스타일 상수 ── */
const NAV_BTN = [
  'flex-shrink-0 flex items-center justify-center w-6 h-6 rounded-full transition-colors',
  'text-[var(--color-neutral-400)] hover:text-[var(--color-neutral-700)] hover:bg-[var(--color-neutral-100)]',
].join(' ');

/* ── 서브컴포넌트 ── */
function NavButton({ onClick, label, children }: { onClick: () => void; label: string; children: ReactNode }) {
  return (
    <button onClick={onClick} className={NAV_BTN} aria-label={label}>
      {children}
    </button>
  );
}

function LegendItem({ dotColor, label }: { dotColor: string; label: string }) {
  return (
    <div className="flex items-center gap-1">
      <span className="w-3 h-3 rounded-full" style={{ backgroundColor: dotColor }} />
      <span className="text-[length:var(--text-sm)] font-semibold text-[var(--color-text-primary)] tracking-[var(--tracking-tight)]">
        {label}
      </span>
    </div>
  );
}

function StatItem({ label, value, valueColor, style }: { label: string; value: number; valueColor: string; style?: React.CSSProperties }) {
  // const { t } = useTranslation();
  return (
    <div className="flex flex-col items-center gap-1">
      <span className="text-[length:var(--text-label3)] font-medium text-[var(--color-neutral-400)] tracking-[-0.325px] leading-[var(--leading-normal)]">
        {label}
      </span>
      <span className={['text-[24px] font-semibold tracking-[-0.025em] leading-[1.4]', valueColor].join(' ')} style={style}>
        {value.toLocaleString('ko-KR')}
        {/* {t('common.count_unit')} */}
      </span>
    </div>
  );
}

/* ── 파이 지오메트리 상수 ── */
const PIE_LR_MARGIN = 24; // PieChart margin: left(12) + right(12)
const MAX_OUTER_R   = 150;

interface SuccessRateChartProps {
  period?: TrendPeriod;
  featureType?: DashFeatureType;
}

function SuccessRateChart({ period, featureType }: SuccessRateChartProps) {
  const { t } = useTranslation();
  const [slide, setSlide] = useState(0);
  const { selectedProject } = useProjectContext();
  const pivotRef = useRef<HTMLDivElement>(null);
  const [pivotWidth, setPivotWidth] = useState(0);

  /* 손바닥은 1:1 촬영/사진 인증 슬라이드 제외 */
  const isPalm = featureType === 'PALM';
  const slideList = SLIDES
    .map((cfg, i) => ({ cfg, ratioKey: RATIO_KEYS[i] }))
    .filter(({ ratioKey }) => !(isPalm && (ratioKey === 'verifyById' || ratioKey === 'verifyByImage')));

  useEffect(() => {
    const el = pivotRef.current;
    if (!el) return;
    const ro = new ResizeObserver(([entry]) => {
      setPivotWidth(entry.contentRect.width);
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  /* featureType 변경 시 슬라이드 초기화 (손바닥↔얼굴 슬라이드 수 다름) */
  useEffect(() => { setSlide(0); }, [featureType]);

  useEffect(() => {
    const id = setInterval(() => {
      setSlide((s) => (s + 1) % slideList.length);
    }, 4000);
    return () => clearInterval(id);
  }, [slideList.length]);

  const { data: ratios } = useQuery({
    queryKey: ['dashboard', 'ratios', selectedProject?.id, period, featureType],
    queryFn:   () => getDashboardRatios(period, featureType).then(r => r.data.data),
    enabled:   !!selectedProject?.apiKey,
    staleTime: 0,
  });

  const safeSlide      = slideList.length ? slide % slideList.length : 0;
  const cfg            = slideList[safeSlide].cfg;
  const ratio          = ratios?.[slideList[safeSlide].ratioKey] ?? EMPTY_RATIO;
  const primaryPct     = ratio.primaryPercent;
  const secondaryPct   = ratio.secondaryPercent;
  const primaryCount   = ratio.primaryCount;
  const secondaryCount = ratio.secondaryCount;

  const DATA = [
    { name: t(cfg.primaryKey),   value: primaryPct,   fill: cfg.primaryColor },
    { name: t(cfg.secondaryKey), value: secondaryPct, fill: '#E2E8F0'        },
  ];

  // 컨테이너 너비 기반 동적 파이 크기 계산
  // outerR: (측정 너비 - 좌우 margin 24px) / 2, 최대 150px 제한
  const outerR    = pivotWidth > 0
    ? Math.min(Math.floor((pivotWidth - PIE_LR_MARGIN) / 2), MAX_OUTER_R)
    : MAX_OUTER_R;
  const innerR    = Math.floor(outerR * 0.7);
  const chartH    = outerR + 24; // 반원 높이 + 여백
  // 퍼센트 텍스트 크기: outerR=150 → 44px, outerR=74 → 24px (최소값)
  const pctFontPx = Math.max(24, Math.round(outerR * 0.293));

  return (
    <Card className="flex flex-col overflow-hidden h-[412px] gap-3">
      <div className="px-4 py-4">
        <p className="text-[length:var(--text-base)] font-medium text-[var(--color-neutral-700)] tracking-[-0.4px] leading-[var(--leading-normal)]">
          {t(cfg.titleKey)}
        </p>
      </div>

      <div className="flex items-center justify-center gap-6 px-3">
        <LegendItem dotColor={cfg.primaryColor} label={t(cfg.primaryKey)} />
        <LegendItem dotColor="#E2E8F0"          label={t(cfg.secondaryKey)} />
      </div>

      <div className="flex items-center justify-center gap-2 px-2">
        <NavButton onClick={() => setSlide((s) => (s - 1 + slideList.length) % slideList.length)} label={t('common.previous')}>
          <ChevronLeftIcon />
        </NavButton>

        <div ref={pivotRef} className="relative flex-1" style={{ height: `${chartH}px` }}>
          <ResponsiveContainer width="100%" height={chartH}>
            <PieChart margin={{ top: 0, right: 12, bottom: 0, left: 12 }}>
              <Pie
                data={DATA}
                cx="50%"
                cy="100%"
                startAngle={180}
                endAngle={0}
                innerRadius={innerR}
                outerRadius={outerR}
                dataKey="value"
                paddingAngle={0}
                strokeWidth={0}
              />
            </PieChart>
          </ResponsiveContainer>

          <div className="absolute bottom-0 left-0 right-0 flex justify-center">
            <span
              style={{ fontSize: `${pctFontPx}px` }}
              className="font-semibold text-[var(--color-neutral-700)] tracking-[var(--tracking-tight)] leading-[var(--leading-normal)]"
            >
              {primaryPct}%
            </span>
          </div>
        </div>

        <NavButton onClick={() => setSlide((s) => (s + 1) % slideList.length)} label={t('common.next')}>
          <ChevronRightIcon />
        </NavButton>
      </div>
     <div className="h-[25px]"></div>
      <div className="flex items-center justify-center h-[46px]">
        <StatItem label={t(cfg.primaryRateKey)}   value={primaryCount}   valueColor="" style={{ color: cfg.primaryColor }} />
        <div className="w-px h-[25px] bg-[var(--color-neutral-300)] mx-[25px]" />
        <StatItem label={t(cfg.secondaryRateKey)} value={secondaryCount} valueColor="text-[var(--color-neutral-600)]" />
      </div>

      <div className="flex items-center justify-center gap-2 pt-2 pb-[30px] mt-auto">
        {slideList.map((_, i) => (
          <button
            key={i}
            onClick={() => setSlide(i)}
            aria-label={`${i + 1}`}
            className={[
              'rounded-full transition-all',
              i === safeSlide
                ? 'w-8 h-2 bg-[var(--color-neutral-500)]'
                : 'w-2 h-2 bg-[var(--color-neutral-600)] opacity-20',
            ].join(' ')}
          />
        ))}
      </div>
    </Card>
  );
}

export default SuccessRateChart;

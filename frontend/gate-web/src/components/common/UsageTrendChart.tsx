import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer,
} from 'recharts';
import { Card } from '@/components/ui';
import { getDashboardTrend } from '@/services/dashboard';
import type { TrendPeriod, DashFeatureType } from '@/services/dashboard';
import { useProjectContext } from '@/contexts/ProjectContext';

function LegendDot({ color, name }: { color: string; name: string }) {
  return (
    <div className="flex items-center gap-1">
      <span className="w-3 h-3 rounded-full" style={{ backgroundColor: color }} />
      <span className="text-[length:var(--text-xxs)] font-medium text-[var(--color-neutral-800)] tracking-[-0.3px] leading-[var(--leading-m)]">
        {name}
      </span>
    </div>
  );
}

interface UsageTrendChartProps {
  period?: TrendPeriod;
  featureType?: DashFeatureType;
}

function UsageTrendChart({ period: externalPeriod, featureType }: UsageTrendChartProps) {
  const { t } = useTranslation();
  const { selectedProject } = useProjectContext();

  const apiPeriod = externalPeriod ?? 'MONTH';
  const isPalm = featureType === 'PALM';

  const { data: trendData } = useQuery({
    queryKey: ['dashboard', 'trend', selectedProject?.id, apiPeriod, featureType],
    queryFn:   () => getDashboardTrend(apiPeriod, featureType).then(r => r.data.data),
    enabled:   !!selectedProject?.apiKey,
    staleTime: 0,
  });

  const chartData = trendData
    ? trendData.labels.map((label, i) => ({
        time: label,
        reg:  trendData.registration[i]  ?? 0,
        v11:  trendData.verifyById[i]    ?? 0,
        v1i:  trendData.verifyByImage[i] ?? 0,
        v1n:  trendData.identify[i]      ?? 0,
        lv:   trendData.liveness[i]      ?? 0,
      }))
    : [];

  const LINES = [
    { key: 'reg', name: t('module.enrollment'),   hex: '#4BBBFB' },
    ...(isPalm ? [] : [
      { key: 'v11', name: t('module.verification'),  hex: '#F59E0B' },
      { key: 'v1i', name: t('module.verify_image'),  hex: '#06B6D4' },
    ]),
    { key: 'v1n', name: t('module.matching'),      hex: '#8A58FF' },
    { key: 'lv',  name: t('module.liveness'),      hex: '#10B981' },
  ];

  return (
    <Card className="flex flex-col overflow-hidden h-[412px]">
      <div className="flex items-center h-[60px] px-4 py-3">
        <p className="text-[length:var(--text-base)] font-medium text-[var(--color-neutral-700)] tracking-[var(--tracking-tight)] leading-[var(--leading-l)]">
          {t('usage_chart.title')}
        </p>
      </div>

      <div className="flex items-center justify-center gap-6 px-4 pb-2">
        {LINES.map(({ name, hex }) => (
          <LegendDot key={name} color={hex} name={name} />
        ))}
      </div>

      <div className="flex-1 min-h-0 px-4 pb-4">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={chartData} margin={{ top: 4, right: 8, left: -20, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#E2E8F0" vertical={false} />
            <XAxis
              dataKey="time"
              tick={{ fontSize: 12, fill: '#94A3B8' }}
              axisLine={false}
              tickLine={false}
            />
            <YAxis
              tick={{ fontSize: 12, fill: '#94A3B8' }}
              axisLine={false}
              tickLine={false}
            />
            <Tooltip
              contentStyle={{
                fontSize: 12,
                border: '1px solid #E2E8F0',
                borderRadius: '6px',
                boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
              }}
            />
            {LINES.map(({ key, name, hex }) => (
              <Line
                key={key}
                type="monotone"
                dataKey={key}
                name={name}
                stroke={hex}
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 4 }}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </Card>
  );
}

export default UsageTrendChart;

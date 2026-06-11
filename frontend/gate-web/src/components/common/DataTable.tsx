import { useState } from 'react';
import { createPortal } from 'react-dom';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { Card } from '@/components/ui';
import { getDashboardDaily } from '@/services/dashboard';
import type { DailyRow, TrendPeriod, DashFeatureType } from '@/services/dashboard';
import { useProjectContext } from '@/contexts/ProjectContext';

const PREVIEW_SIZE = 5;   // 대시보드 미리보기 행 수
const MODAL_SIZE   = 12;  // 모달 페이지당 행 수

interface Column { key: string; label: string }

/* ── 스타일 상수 ── */
const TH_BASE = 'px-4 py-3 text-[14px] font-semibold text-[#475569] tracking-[-0.35px] text-center whitespace-nowrap bg-white';
const TD_BASE = 'px-4 py-3 text-right whitespace-nowrap align-middle border-r border-[#e2e8f0] last:border-r-0';
const NAV_BTN = [
  'flex items-center justify-center w-7 h-7 rounded-md transition-colors text-sm',
  'text-[var(--color-neutral-600)] hover:bg-[var(--color-neutral-100)]',
  'disabled:opacity-30 disabled:cursor-not-allowed',
].join(' ');

/* ── 아이콘 ── */
const ChevronLeftIcon  = () => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="15 18 9 12 15 6" /></svg>;
const ChevronRightIcon = () => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="9 18 15 12 9 6" /></svg>;
const CloseIcon        = () => <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>;

function TableHeaderCell({ label }: { label: string }) {
  return <th className={[TH_BASE, 'w-[16.666%]'].join(' ')}>{label}</th>;
}
function TableDataCell({ children }: { children: ReactNode }) {
  return <td className={TD_BASE}>{children}</td>;
}

/* 숫자 + 단위 분리 렌더링 */
function NumCell({ n }: { n?: number }) {
  const { t } = useTranslation();
  return (
    <span className="flex items-baseline gap-1 justify-end">
      <span className="text-[14px] font-medium text-[#334155] tracking-[-0.35px]">
        {n?.toLocaleString('ko-KR') ?? '-'}
      </span>
      <span className="text-[13px] font-normal text-[#64748b] tracking-[-0.325px]">{t('common.count_unit')}</span>
    </span>
  );
}

/* ── 테이블 바디 공통 ── */
function TableBody({ rows, colSpan }: { rows: DailyRow[]; columns?: Column[]; colSpan: number }) {
  const { t } = useTranslation();
  if (rows.length === 0) {
    return (
      <tr>
        <td colSpan={colSpan} className="px-4 py-8 text-center text-[14px] text-[var(--color-neutral-400)]">
          {t('common.no_data')}
        </td>
      </tr>
    );
  }
  return (
    <>
      {rows.map((row) => (
        <tr
          key={row.date}
          className={[
            'hover:bg-[var(--color-neutral-100)] transition-colors',
            'shadow-[inset_0px_-1px_0px_0px_#e2e8f0]',
          ].join(' ')}
        >
          <td className="px-4 py-3 text-[14px] font-medium text-[#334155] tracking-[-0.35px] whitespace-nowrap align-middle text-center border-r border-[#e2e8f0]">
            {row.date}
          </td>
          <TableDataCell><NumCell n={row.registration} /></TableDataCell>
          <TableDataCell><NumCell n={row.verifyById} /></TableDataCell>
          <TableDataCell><NumCell n={row.verifyByImage} /></TableDataCell>
          <TableDataCell><NumCell n={row.identify} /></TableDataCell>
          <TableDataCell><NumCell n={row.liveness} /></TableDataCell>
        </tr>
      ))}
    </>
  );
}

/* ── 더보기 모달 ── */
function DataTableModal({
  onClose,
  periodLabel,
  columns,
  featureType,
}: {
  onClose:     () => void;
  periodLabel: string;
  columns:     Column[];
  featureType?: DashFeatureType;
}) {
  const { t } = useTranslation();
  const { selectedProject } = useProjectContext();
  const [page, setPage] = useState(1);

  const { data } = useQuery({
    queryKey:        ['dashboard', 'daily-modal', selectedProject?.id, page, featureType],
    queryFn:         () => getDashboardDaily(page, MODAL_SIZE, featureType).then(r => r.data.data),
    enabled:         !!selectedProject?.apiKey,
    placeholderData: keepPreviousData,
    staleTime:       0,
  });

  const rows       = data?.contents ?? [];
  const totalPages = data?.page.totalPages ?? 1;
  const total      = data?.page.totalElements ?? 0;
  const from       = (page - 1) * MODAL_SIZE + 1;
  const to         = Math.min(page * MODAL_SIZE, total);

  return createPortal(
    <div className="fixed inset-0 z-[var(--z-modal)] flex">
      <div
        className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]"
        onClick={onClose}
      />
      <div
        className={[
          'absolute top-[26px] right-[18px]',
          'w-[750px] max-w-[calc(100vw-36px)]',
          'h-[calc(100vh-53px)]',
          'bg-white rounded-[34px]',
          'flex flex-col overflow-hidden',
          'shadow-[var(--shadow-xl)]',
        ].join(' ')}
        onClick={e => e.stopPropagation()}
      >
        {/* 콘텐츠 래퍼 — px-[36px] py-[40px] */}
        <div className="flex flex-col h-full px-9 py-10">

          {/* 헤더 */}
          <div className="flex items-center justify-between mb-6 flex-shrink-0">
            <div className="flex items-center gap-3">
              <h2 className="text-[20px] font-semibold text-[#1e293b] tracking-[-0.5px]">
                {t('data_table.title')}
              </h2>
              {periodLabel && (
                <span className="text-[14px] font-medium text-[#64748b] tracking-[-0.35px]">
                  {periodLabel}
                </span>
              )}
            </div>
            <button
              onClick={onClose}
              className="w-9 h-9 flex items-center justify-center rounded-full hover:bg-[#f1f5f9] transition-colors text-[#94a3b8]"
            >
              <CloseIcon />
            </button>
          </div>

          {/* 테이블 */}
          <div className="flex-1 overflow-y-auto min-h-0">
            <table className="w-full table-fixed">
              <thead className="sticky top-0 bg-white">
                <tr className="border-b border-[#1e293b]">
                  {columns.map(col => <TableHeaderCell key={col.key} label={col.label} />)}
                </tr>
              </thead>
              <tbody>
                <TableBody rows={rows} columns={columns} colSpan={columns.length} />
              </tbody>
            </table>
          </div>

          {/* 페이지네이션 */}
          <div className="flex items-center justify-between pt-4 border-t border-[#e2e8f0] flex-shrink-0 mt-2">
            <span className="text-[13px] text-[#64748b] tracking-[-0.325px]">
              {t('data_table.pagination', { total, from, to })}
            </span>
            <div className="flex items-center gap-3">
              <span className="text-[13px] text-[#64748b] tracking-[-0.325px]">{t('data_table.page_nav')}</span>
              <div className="flex items-center gap-1.5">
                <div className="w-8 h-8 flex items-center justify-center border border-[#e8eef2] rounded-[4px] text-[12px] text-[#94a3b8]">
                  {page}
                </div>
                <span className="text-[13px] text-[#334155] tracking-[-0.325px]">/ {totalPages}</span>
              </div>
              <div className="flex items-center gap-1">
                <button className={NAV_BTN} onClick={() => setPage(p => p - 1)} disabled={page <= 1}><ChevronLeftIcon /></button>
                <button className={NAV_BTN} onClick={() => setPage(p => p + 1)} disabled={page >= totalPages}><ChevronRightIcon /></button>
              </div>
            </div>
          </div>

          {/* 확인 버튼 */}
          <button
            onClick={onClose}
            className="mt-5 w-full py-3 bg-[#f1f5f9] rounded-[8px] text-[18px] font-semibold text-[#334155] tracking-[-0.45px] hover:bg-[#e2e8f0] transition-colors flex-shrink-0"
          >
            {t('common.confirm')}
          </button>

        </div>
      </div>
    </div>,
    document.body
  );
}

/* ── DataTable Props ── */
interface DataTableProps {
  period?:      TrendPeriod;
  featureType?: DashFeatureType;
  periodLabel?: string;
}

/* ── 메인 컴포넌트 ── */
function DataTable({ period: _period, featureType, periodLabel = '' }: DataTableProps) {
  const { t } = useTranslation();
  const { selectedProject } = useProjectContext();
  const [modalOpen, setModalOpen] = useState(false);
  const isPalm = featureType === 'PALM';

  const { data: dailyData } = useQuery({
    queryKey:        ['dashboard', 'daily', selectedProject?.id, featureType],
    queryFn:         () => getDashboardDaily(1, PREVIEW_SIZE, featureType).then(r => r.data.data),
    enabled:         !!selectedProject?.apiKey,
    placeholderData: keepPreviousData,
    staleTime:       0,
  });

  const rows = dailyData?.contents ?? [];

  /* 손바닥은 1:1 촬영/사진 인증 컬럼 제외 */
  const COLUMNS: Column[] = [
    { key: 'date',         label: t('data_table.date')      },
    { key: 'registration', label: t('module.enrollment')    },
    ...(isPalm ? [] : [
      { key: 'verifyById',   label: t('module.verification')  },
      { key: 'verifyByImage',label: t('module.verify_image')  },
    ]),
    { key: 'identify',     label: t('module.matching')      },
    { key: 'liveness',     label: t('module.liveness')      },
  ];

  return (
    <>
      <Card className="overflow-hidden">
        <div className="flex items-center justify-between px-4 pt-4 pb-3">
          <p className="text-[length:var(--text-base)] font-medium text-[var(--color-neutral-700)] tracking-[-0.4px]">
            {t('data_table.title')}
          </p>
          <button
            onClick={() => setModalOpen(true)}
            className="flex items-center gap-0.5 text-[13px] font-medium text-[#64748b] tracking-[-0.325px] hover:opacity-80 transition-opacity"
          >
            {t('common.view_more')}
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="9 18 15 12 9 6" />
            </svg>
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[560px] table-fixed">
            <thead>
              <tr className="border-b border-[#1e293b]">
                {COLUMNS.map(col => <TableHeaderCell key={col.key} label={col.label} />)}
              </tr>
            </thead>
            <tbody>
              <TableBody rows={rows} columns={COLUMNS} colSpan={COLUMNS.length} />
            </tbody>
          </table>
        </div>
      </Card>

      {modalOpen && (
        <DataTableModal
          onClose={() => setModalOpen(false)}
          periodLabel={periodLabel}
          columns={COLUMNS}
          featureType={featureType}
        />
      )}
    </>
  );
}

export default DataTable;

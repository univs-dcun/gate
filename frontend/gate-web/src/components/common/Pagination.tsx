/**
 * Pagination — 테이블 하단 페이지네이션 공통 컴포넌트
 *
 * 왼쪽: {from}-{to} of {filtered} (전체: {total})
 * 오른쪽: [{size}개씩 ▼] [<] [{page} / {totalPages} 페이지 ▼] [>]
 */

import { useTranslation } from 'react-i18next';

export interface PaginationProps {
  pageNum:        number;
  pageSize:       number;
  totalPages:     number;
  totalElements:  number;  // 필터된 총 레코드 수
  currentCount:   number;  // 현재 페이지 아이템 수
  totalRecords?:  number;  // 전체 레코드 수 (optional)
  onPageChange:   (page: number) => void;
  onPageSizeChange: (size: number) => void;
  pageSizeOptions?: number[];
}

/* ── 아이콘 ── */
const ChevronLeftIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="15 18 9 12 15 6" />
  </svg>
);
const ChevronRightIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="9 18 15 12 9 6" />
  </svg>
);

/* ── 스타일 상수 ── */
const SELECT_CLS = [
  'h-8 px-2 rounded-[4px]',
  'border border-[#E8EEF2] bg-white',
  'text-[12px] text-[var(--color-text-primary)] tracking-[-0.025px]',
  'focus:outline-none cursor-pointer',
].join(' ');

const NAV_BTN = [
  'flex items-center justify-center w-8 h-8 rounded-[4px]',
  'border border-[#E8EEF2] bg-white',
  'text-[var(--color-neutral-500)] transition-colors',
  'hover:bg-[var(--color-neutral-100)] disabled:opacity-40 disabled:cursor-not-allowed',
].join(' ');

export default function Pagination({
  pageNum,
  pageSize,
  totalPages,
  totalElements,
  currentCount,
  totalRecords: _totalRecords,
  onPageChange,
  onPageSizeChange,
  pageSizeOptions = [10, 20, 50, 100],
}: PaginationProps) {
  const { t } = useTranslation();

  const from = totalElements === 0 ? 0 : (pageNum - 1) * pageSize + 1;
  const to   = (pageNum - 1) * pageSize + currentCount;
  const safeTotalPages = Math.max(1, totalPages);

  return (
    <div className="flex items-center justify-between mt-10">

      {/* 왼쪽: 범위 + 전체 수 */}
      <div className="flex items-center gap-1.5">
        <span className="text-[14px] font-normal text-[var(--color-neutral-600)] tracking-[-0.025px] leading-[20px]">
          {t('pagination.range', { from, to, filtered: totalElements.toLocaleString() })}
        </span>
        {/* total_count 숨김 */}
      </div>

      {/* 오른쪽: 페이지 크기 + 이전 + 페이지 이동 + 다음 */}
      <div className="flex items-center gap-2">

        {/* 페이지당 개수 */}
        <select
          value={pageSize}
          onChange={(e) => { onPageSizeChange(Number(e.target.value)); }}
          className={SELECT_CLS}
        >
          {pageSizeOptions.map((s) => (
            <option key={s} value={s}>
              {s} {t('common.items')}
            </option>
          ))}
        </select>

        {/* 이전 페이지 */}
        <button
          type="button"
          onClick={() => onPageChange(Math.max(1, pageNum - 1))}
          disabled={pageNum === 1}
          className={NAV_BTN}
        >
          <ChevronLeftIcon />
        </button>

        {/* 페이지 이동 combobox */}
        <select
          value={pageNum}
          onChange={(e) => onPageChange(Number(e.target.value))}
          className={[SELECT_CLS, 'min-w-[108px]'].join(' ')}
        >
          {Array.from({ length: safeTotalPages }, (_, i) => i + 1).map((p) => (
            <option key={p} value={p}>
              {t('pagination.page_of', { page: p, total: safeTotalPages })}
            </option>
          ))}
        </select>

        {/* 다음 페이지 */}
        <button
          type="button"
          onClick={() => onPageChange(Math.min(safeTotalPages, pageNum + 1))}
          disabled={pageNum >= safeTotalPages}
          className={NAV_BTN}
        >
          <ChevronRightIcon />
        </button>

      </div>
    </div>
  );
}

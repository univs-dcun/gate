interface PaginationProps {
  total: number;      // 전체 항목 수
  page: number;       // 현재 페이지 (1-based)
  pageSize?: number;  // 페이지당 항목 수
  onChange?: (page: number) => void;
}

const ChevronLeft = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="15 18 9 12 15 6" />
  </svg>
);
const ChevronRight = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="9 18 15 12 9 6" />
  </svg>
);

/**
 * Figma 로그 상세 화면 기반 Pagination 컴포넌트
 *
 * [1 - 10 of N Pages] ···· [The page on [P▼]] [◀] [▶]
 */
function Pagination({ total, page, pageSize = 10, onChange }: PaginationProps) {
  const totalPages = Math.ceil(total / pageSize);
  const start = (page - 1) * pageSize + 1;
  const end = Math.min(page * pageSize, total);

  const pages = Array.from({ length: totalPages }, (_, i) => i + 1);

  return (
    <div className="flex items-center justify-between px-4 py-3 border-t border-[var(--color-border-default)]">
      {/* 좌측: 범위 텍스트 */}
      <span className="text-xs text-[var(--color-text-secondary)]">
        {start} - {end} of {total} Pages
      </span>

      {/* 우측: 페이지 이동 */}
      <div className="flex items-center gap-2">
        <span className="text-xs text-[var(--color-text-secondary)]">The page on</span>

        {/* 페이지 드롭다운 */}
        <select
          value={page}
          onChange={(e) => onChange?.(Number(e.target.value))}
          className="h-7 px-2 text-xs border border-[var(--color-border-default)] rounded-[var(--radius-sm)]
                     bg-white text-[var(--color-text-primary)] outline-none
                     focus:border-[var(--color-border-focus)] cursor-pointer"
        >
          {pages.map((p) => (
            <option key={p} value={p}>{p}</option>
          ))}
        </select>

        {/* 이전/다음 */}
        <button
          onClick={() => onChange?.(page - 1)}
          disabled={page <= 1}
          className="flex h-7 w-7 items-center justify-center rounded-[var(--radius-sm)]
                     border border-[var(--color-border-default)] bg-white
                     text-[var(--color-text-secondary)] transition-colors
                     hover:bg-[var(--color-neutral-100)] disabled:opacity-40 disabled:cursor-not-allowed"
          aria-label="이전 페이지"
        >
          <ChevronLeft />
        </button>
        <button
          onClick={() => onChange?.(page + 1)}
          disabled={page >= totalPages}
          className="flex h-7 w-7 items-center justify-center rounded-[var(--radius-sm)]
                     border border-[var(--color-border-default)] bg-white
                     text-[var(--color-text-secondary)] transition-colors
                     hover:bg-[var(--color-neutral-100)] disabled:opacity-40 disabled:cursor-not-allowed"
          aria-label="다음 페이지"
        >
          <ChevronRight />
        </button>
      </div>
    </div>
  );
}

export default Pagination;

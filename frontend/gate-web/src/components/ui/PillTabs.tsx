export interface PillTab {
  id: string;
  label: string;
  count?: number;
}

interface PillTabsProps {
  tabs: PillTab[];
  activeId: string;
  onChange: (id: string) => void;
}

/**
 * Figma 로그 상세 화면 기반 알약형(Pill) 탭 바
 *
 * - active: Link_Blue 배경 + 흰 텍스트 + rounded-full
 * - inactive: 텍스트만 (neutral-500) + hover 시 neutral-100 배경
 * - count 있을 경우 탭 label 옆에 표시: "전체 (12)"
 */
function PillTabs({ tabs, activeId, onChange }: PillTabsProps) {
  return (
    <div className="flex items-center gap-1.5 flex-wrap">
      {tabs.map((tab) => {
        const isActive = tab.id === activeId;
        const label = tab.count !== undefined ? `${tab.label} (${tab.count})` : tab.label;
        return (
          <button
            key={tab.id}
            onClick={() => onChange(tab.id)}
            className={[
              'px-4 py-1.5 text-sm font-medium rounded-full transition-colors duration-[var(--transition-fast)]',
              isActive
                ? 'bg-[var(--color-link-blue)] text-white'
                : 'text-[var(--color-text-secondary)] hover:bg-[var(--color-neutral-100)] hover:text-[var(--color-text-primary)]',
            ].join(' ')}
          >
            {label}
          </button>
        );
      })}
    </div>
  );
}

export default PillTabs;

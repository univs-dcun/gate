import type { ReactNode } from 'react';

interface NavItem {
  id: string;
  icon: ReactNode;
  label: string;
  href?: string;
}

interface IconSidebarProps {
  items: NavItem[];
  activeId?: string;
  onSelect?: (id: string) => void;
  bottomItems?: NavItem[];
}

/**
 * Figma 메인 화면 기반: 좌측 아이콘 전용 사이드바
 * - 배경: --sidebar-icon-bg (다크 #16181D)
 * - 아이콘 너비: --icon-sidebar-width (52px)
 * - 활성 아이콘: Link_Blue 틴트 배경 + blue 색상
 */
function IconSidebar({ items, activeId, onSelect, bottomItems = [] }: IconSidebarProps) {
  return (
    <aside
      className="fixed left-0 top-0 bottom-0 flex flex-col items-center py-3 z-[var(--z-sticky)]"
      style={{
        width: 'var(--icon-sidebar-width)',
        background: 'var(--sidebar-icon-bg)',
      }}
    >
      {/* 상단 로고 */}
      <div className="mb-4 flex h-9 w-9 items-center justify-center rounded-[var(--radius-md)] bg-[var(--color-link-blue)]">
        <span className="text-white font-bold text-sm">U</span>
      </div>

      <div className="w-full h-px bg-white/10 mb-3" />

      {/* 메인 내비게이션 아이콘 */}
      <nav className="flex flex-col items-center gap-1 flex-1 w-full px-2">
        {items.map((item) => {
          const isActive = item.id === activeId;
          return (
            <button
              key={item.id}
              title={item.label}
              onClick={() => onSelect?.(item.id)}
              className={[
                'flex h-9 w-9 items-center justify-center rounded-[var(--radius-md)] transition-colors duration-[var(--transition-fast)]',
                isActive
                  ? 'bg-[var(--sidebar-icon-active-bg)] text-[var(--sidebar-icon-active-color)]'
                  : 'text-[var(--sidebar-icon-color)] hover:bg-[var(--sidebar-icon-hover-bg)] hover:text-white',
              ].join(' ')}
            >
              {item.icon}
            </button>
          );
        })}
      </nav>

      {/* 하단 아이콘 (프로필 등) */}
      {bottomItems.length > 0 && (
        <div className="flex flex-col items-center gap-1 w-full px-2 mt-2">
          <div className="w-full h-px bg-white/10 mb-2" />
          {bottomItems.map((item) => {
            const isActive = item.id === activeId;
            return (
              <button
                key={item.id}
                title={item.label}
                onClick={() => onSelect?.(item.id)}
                className={[
                  'flex h-9 w-9 items-center justify-center rounded-[var(--radius-md)] transition-colors duration-[var(--transition-fast)]',
                  isActive
                    ? 'bg-[var(--sidebar-icon-active-bg)] text-[var(--sidebar-icon-active-color)]'
                    : 'text-[var(--sidebar-icon-color)] hover:bg-[var(--sidebar-icon-hover-bg)] hover:text-white',
                ].join(' ')}
              >
                {item.icon}
              </button>
            );
          })}
        </div>
      )}
    </aside>
  );
}

export default IconSidebar;

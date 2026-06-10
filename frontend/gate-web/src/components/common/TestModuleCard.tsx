import type { ReactNode } from 'react';

export interface TestModule {
  id: string;
  category: string;   // 등록 / 1:1 확인 / 1:N 매칭 / 라이브니스
  title: string;      // 등록 모듈 테스트 / 신원 확인 테스트 / …
  icon: ReactNode;    // 아이콘 SVG
  iconBg: string;     // 아이콘 원 배경색 (e.g. '#edf9ff')
  isActive?: boolean;
}

interface TestModuleCardProps {
  module: TestModule;
  isSelected: boolean;
  onClick: (module: TestModule) => void;
}

const ChevronRight = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <polyline points="9 18 15 12 9 6" />
  </svg>
);

/**
 * 모듈 테스트 페이지 좌측 패널 — 테스트 모듈 카드 (Figma 250:2175)
 *
 * selected  — border-2 link-blue + 오른쪽 화살표 표시
 * default   — border #e8eef2 + 화살표 없음
 *
 * 카드 내부:
 *   좌측: 46px 아이콘 원 (iconBg)
 *   중앙: category 14px Regular #757b80 / title 18px Bold #4a4b4d
 *   우측: ChevronRight (선택 시에만)
 */
function TestModuleCard({ module, isSelected, onClick }: TestModuleCardProps) {
  return (
    <button
      onClick={() => onClick(module)}
      className={[
        'w-full flex items-center gap-3 px-3 py-5 text-left rounded-[var(--card-radius)] bg-white transition-all border',
        isSelected
          ? 'border-2 border-[var(--color-link-blue)]'
          : 'border border-[#e8eef2]',
      ].join(' ')}
    >
      {/* 아이콘 원 46px */}
      <span
        className="flex-shrink-0 w-[40px] h-[40px] rounded-full flex items-center justify-center p-2"
        style={{ backgroundColor: module.iconBg }}
      >
        {module.icon}
      </span>

      {/* 텍스트 영역 */}
      <div className="flex-1 min-w-0 flex flex-col gap-0.5">
        <span className="text-[length:var(--text-sm)] font-normal leading-[var(--leading-normal)] tracking-[-0.35px]"
          style={{ color: '#757b80' }}>
          {module.category}
        </span>
        <span className="text-[16px] font-bold leading-[var(--leading-normal)] tracking-[-0.4px]"
          style={{ color: '#4a4b4d' }}>
          {module.title}
        </span>
      </div>

      {/* 우측 화살표 (선택 시에만) */}
      {isSelected && (
        <span className="flex-shrink-0 text-[var(--color-link-blue)]">
          <ChevronRight />
        </span>
      )}
    </button>
  );
}

export default TestModuleCard;

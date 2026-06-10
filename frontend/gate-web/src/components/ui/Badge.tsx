import type { ReactNode } from 'react';
import type { ColorScheme } from '@/types';

interface BadgeProps {
  children: ReactNode;
  color?: ColorScheme | 'blue';
  size?: 'sm' | 'md';
  dot?: boolean;
}

/**
 * Figma 기반 Badge / 태그 컴포넌트
 *
 * color:
 *   default  — neutral (회색)
 *   success  — Task_Fg (초록) - 공개
 *   error    — entry_Rg (빨강) - 오류/위험
 *   warning  — learning_Dy (주황) - 진행중
 *   info / blue — Link_Blue 틴트 - 정보
 */

/* ── color별 wrapper + dot 스타일을 하나의 Record로 통합 ── */
const BADGE_CONFIG: Record<string, { wrapper: string; dot: string }> = {
  default: {
    wrapper: 'bg-[var(--color-neutral-100)] text-[var(--color-neutral-600)]',
    dot:     'bg-[var(--color-neutral-400)]',
  },
  success: {
    wrapper: 'bg-[var(--color-task-bg)] text-[var(--color-task)]',
    dot:     'bg-[var(--color-task)]',
  },
  error: {
    wrapper: 'bg-[var(--color-entry-bg)] text-[var(--color-entry)]',
    dot:     'bg-[var(--color-entry)]',
  },
  warning: {
    wrapper: 'bg-[var(--color-learning-bg)] text-[var(--color-learning)]',
    dot:     'bg-[var(--color-learning)]',
  },
  info: {
    wrapper: 'bg-[var(--color-blue-10)] text-[var(--color-link-blue)]',
    dot:     'bg-[var(--color-link-blue)]',
  },
  blue: {
    wrapper: 'bg-[var(--color-blue-10)] text-[var(--color-link-blue)]',
    dot:     'bg-[var(--color-link-blue)]',
  },
};

/* ── size별 스타일 ── */
const SIZE_STYLES: Record<string, string> = {
  sm: 'px-2 py-0.5 text-xs rounded-[var(--radius-sm)]',
  md: 'px-2.5 py-1 text-xs rounded-[var(--radius-md)]',
};

function Badge({ children, color = 'default', size = 'sm', dot = false }: BadgeProps) {
  const config = BADGE_CONFIG[color] ?? BADGE_CONFIG.default;

  return (
    <span
      className={[
        'inline-flex items-center gap-1 font-medium whitespace-nowrap',
        SIZE_STYLES[size],
        config.wrapper,
      ].join(' ')}
    >
      {dot && (
        <span className={['w-1.5 h-1.5 rounded-full flex-shrink-0', config.dot].join(' ')} />
      )}
      {children}
    </span>
  );
}

export default Badge;

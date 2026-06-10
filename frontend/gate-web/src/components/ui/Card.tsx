import type { ReactNode } from 'react';

interface CardProps {
  children: ReactNode;
  className?: string;
}

/**
 * 공통 카드 래퍼
 * bg / border / radius / shadow 토큰을 캡슐화하여 반복 제거
 */
function Card({ children, className }: CardProps) {
  return (
    <div
      className={[
        'bg-[var(--card-bg)] border border-[var(--card-border)]',
        'rounded-[var(--card-radius)] shadow-[var(--card-shadow)]',
        className,
      ].filter(Boolean).join(' ')}
    >
      {children}
    </div>
  );
}

export default Card;

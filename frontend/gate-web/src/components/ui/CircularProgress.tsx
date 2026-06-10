interface CircularProgressProps {
  percentage: number;   // 0 ~ 100
  size?: number;        // SVG 지름 (px)
  strokeWidth?: number;
  color: string;        // 프로그레스 아크 색상
  trackColor?: string;  // 배경 트랙 색상
  showLabel?: boolean;
  labelColor?: string;  // 퍼센트 텍스트 색상 (기본값: --color-text-primary)
}

/**
 * SVG 기반 원형 프로그레스 링
 * Figma 대시보드 StatCard에 사용
 */
function CircularProgress({
  percentage,
  size = 80,
  strokeWidth = 7,
  color,
  trackColor = 'var(--color-neutral-200)',
  showLabel = true,
  labelColor,
}: CircularProgressProps) {
  const r = (size - strokeWidth) / 2;
  const cx = size / 2;
  const cy = size / 2;
  const circumference = 2 * Math.PI * r;
  const offset = circumference * (1 - Math.min(100, Math.max(0, percentage)) / 100);

  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      {/* 회전: 12시 방향에서 시작 */}
      <svg
        width={size}
        height={size}
        style={{ transform: 'rotate(-90deg)' }}
        aria-hidden="true"
      >
        {/* 배경 트랙 */}
        <circle
          cx={cx} cy={cy} r={r}
          fill="none"
          strokeWidth={strokeWidth}
          style={{ stroke: trackColor }}
        />
        {/* 프로그레스 아크 */}
        <circle
          cx={cx} cy={cy} r={r}
          fill="none"
          strokeWidth={strokeWidth}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          style={{ stroke: color, transition: 'stroke-dashoffset 0.5s ease' }}
        />
      </svg>

      {/* 퍼센트 텍스트 (절대 위치, SVG 회전 영향 없음) */}
      {showLabel && (
        <span
          className="absolute font-semibold"
          style={{ fontSize: size < 60 ? '10px' : '16px', color: labelColor ?? 'var(--color-text-primary)' }}
        >
          {percentage}%
        </span>
      )}
    </div>
  );
}

export default CircularProgress;

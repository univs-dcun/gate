interface LogoProps {
  className?: string;
  /** 로고 전체 높이 (px). 너비는 비율(129:20)로 자동 조정됩니다. */
  height?: number;
  /** 너비를 명시적으로 지정 (미지정 시 height 비율로 자동) */
  width?: number;
}

// 로고 SVG viewBox 가로:세로 비율 (Figma node 1735:8337)
const LOGO_ASPECT = 129 / 20; // ≈ 6.45

/**
 * UNIVS GATE 로고 — Figma node 1735:8337
 * 심볼 마크 + UNIVS GATE 워드마크가 합쳐진 단일 SVG(public/icons/logo/logo-full.svg)
 *
 * 사용 예)
 *   <Logo height={22} />   // 사이드바 헤더
 *   <Logo height={48} />   // 랜딩 페이지
 */
function Logo({ className, height = 22, width }: LogoProps) {
  // height = 실제 렌더 높이(px). width 지정 시 그 값으로, 아니면 비율(129:20) 자동
  const w = width ?? Math.round(height * LOGO_ASPECT);
  return (
    <img
      src="/icons/logo/logo-full.svg"
      alt="UNIVS GATE"
      className={className}
      width={w}
      height={height}
      style={{ display: 'block', height, width: w }}
    />
  );
}

export default Logo;

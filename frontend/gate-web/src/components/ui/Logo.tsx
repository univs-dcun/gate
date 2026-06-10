interface LogoProps {
  className?: string;
  /** 로고 전체 높이 (px). 너비는 비율(129:20)로 자동 조정됩니다. */
  height?: number;
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
function Logo({ className, height = 30 }: LogoProps) {
  // 기존 호출부 높이 호환: 이전 로고의 시각적 높이(심볼 = 0.75h)에 맞춤
  const h = Math.round(height * 0.75);

  return (
    <img
      src="/icons/logo/logo-full.svg"
      alt="UNIVS GATE"
      className={className}
      width={Math.round(h * LOGO_ASPECT)}
      height={h}
      style={{ display: 'block', height: h, width: 'auto' }}
    />
  );
}

export default Logo;

/**
 * Figma 커스텀 아이콘 컴포넌트 — node 93:14647 가이드 기반
 * 실제 SVG 파일: public/icons/
 *
 * 사용법:
 *   import { FaceIdIcon, FingerprintIcon } from '@/components/ui/icons';
 *   <FaceIdIcon size={24} className="text-blue-500" />
 */

interface IconProps {
  size?: number;
  className?: string;
  style?: React.CSSProperties;
  alt?: string;
}

function makeIcon(src: string, defaultAlt: string) {
  return function Icon({ size = 24, className, style, alt }: IconProps) {
    return (
      <img
        src={src}
        alt={alt ?? defaultAlt}
        width={size}
        height={size}
        className={className}
        /* Tailwind preflight의 img{height:auto} 무력화 — 세로형 SVG가 늘어나지 않도록 박스 고정 + 비율 유지 */
        style={{ width: size, height: size, objectFit: 'contain', ...style }}
        aria-hidden={!alt}
      />
    );
  };
}

/* ── 생체 인증 ── */
export const FaceIdIcon      = makeIcon('/icons/ic-face-id.svg',     'Face ID');
export const FingerprintIcon = makeIcon('/icons/ic-plam.svg', 'Plam');
export const PlamIcon        = makeIcon('/icons/ic-plam.svg', 'Plam');

/* ── 글로벌 / 언어 ── */
export const LanguageIcon = makeIcon('/icons/ic-language.svg', 'Language');

/* ── 테스트 모듈 아이콘 (Figma node 1800:8049) — 모듈별 고유 아이콘(색상 baked) ── */
const MODULE_ICON_SRC: Record<string, string> = {
  '등록':        '/icons/ic-module-register.svg',
  '1:1 촬영인증': '/icons/ic-module-verify-id.svg',
  '1:1 사진인증': '/icons/ic-module-verify-image.svg',
  '1:N 매칭':    '/icons/ic-module-identify.svg',
  '라이브니스':   '/icons/ic-module-liveness.svg',
};
export function ModuleTypeIcon({ module, size = 20, className }: { module: string; size?: number; className?: string }) {
  const src = MODULE_ICON_SRC[module] ?? MODULE_ICON_SRC['등록'];
  return <img src={src} alt="" width={size} height={size} className={className} aria-hidden />;
}

/* ── 인증 방식 배지 (Figma node 1735:7055) — 얼굴=블루 틴트 / 손바닥=퍼플 틴트 pill ── */
export function AuthMethodBadge({ palm, label }: { palm: boolean; label: string }) {
  const c = palm
    ? { bg: '#f5f2ff', border: '#dbcbff', text: 'var(--color-purple)',    icon: '/icons/ic-authbadge-palm.svg' }
    : { bg: '#eff9ff', border: '#d9e9ff', text: 'var(--color-link-blue)', icon: '/icons/ic-authbadge-face.svg' };
  return (
    <span
      className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full border whitespace-nowrap"
      style={{ backgroundColor: c.bg, borderColor: c.border, color: c.text }}
    >
      {/* 디자인 시스템(node 1738:7453): 아이콘 박스 16px. 얼굴 face-id 글리프는 inset 9.38%(≈1.5px) */}
      <img
        src={c.icon}
        alt=""
        className={['block shrink-0 w-4 h-4 object-contain', palm ? '' : 'p-[1.5px]'].join(' ')}
        aria-hidden
      />
      <span className="text-[14px] font-semibold leading-5 tracking-[-0.35px]">{label}</span>
    </span>
  );
}

/* ── 로그 필터 (Figma node 1735:6973) ── */
export const SearchBlueIcon   = makeIcon('/icons/ic-search.svg',        'Search');
export const RefreshIcon      = makeIcon('/icons/ic-refresh.svg',       'Reset');
export const FilterModuleIcon = makeIcon('/icons/ic-filter-module.svg', 'Module');
export const FilterResultIcon = makeIcon('/icons/ic-filter-result.svg', 'Result');

/* ── 로그인 폼 ── */
export const LoginIdIcon          = makeIcon('/icons/ic-login-id.svg',       'ID / Email');
export const LoginEmailIcon       = makeIcon('/icons/ic-login-email.svg',    'Email');
export const LoginPasswordIcon    = makeIcon('/icons/ic-login-password.svg', 'Password');
export const VisibilityIcon       = makeIcon('/icons/ic-visibility.svg',     'Show password');
export const VisibilityOffIcon    = makeIcon('/icons/ic-visibility-off.svg', 'Hide password');

/* ── 프로젝트 / 플랜 ── */
export const PlanIcon            = makeIcon('/icons/ic-plan.svg',         'Plan');
export const ProjectGoalIcon     = makeIcon('/icons/ic-project-goal.svg', 'Project goal');
export const ProjectNameIcon     = makeIcon('/icons/ic-project-name.svg', 'Project name');
export const TypeIcon            = makeIcon('/icons/ic-type.svg',         'Type');
export const AddPersonIcon       = makeIcon('/icons/ic-add-person.svg',   'Add person');
export const CodeIcon            = makeIcon('/icons/code.svg',            'Standard');
export const ExternalLinkIcon    = makeIcon('/icons/external-link.svg',   'External');

import { useTranslation } from 'react-i18next';
import { FaceIdIcon, FingerprintIcon } from '@/components/ui/icons';

/**
 * AuthMethodTabs — 인증방식 선택 탭 (얼굴 / 손바닥)
 *
 * Figma node 1722:5518 (탭) 기반. 손바닥 인증 추가에 따른 신규 컴포넌트.
 *  - 선택: SemiBold + neutral-800 + 하단 보더
 *  - 미선택: Regular + neutral-400
 *  - 아이콘 32px + 라벨 20px(Typography/Size/600), gap-8, px-12 py-8, 탭 간 gap-20
 */

export type AuthMethod = 'face' | 'palm';

interface AuthMethodTabsProps {
  value: AuthMethod;
  onChange: (value: AuthMethod) => void;
  className?: string;
}

const TAB_BASE = 'flex items-center gap-2 px-3 py-2 transition-colors';
const TAB_ACTIVE = 'border-b border-solid border-[var(--color-neutral-800)]';
const LABEL_BASE = 'text-[20px] leading-[32px] tracking-[-0.5px] whitespace-nowrap';
const LABEL_ACTIVE = 'font-semibold text-[var(--color-neutral-800)]';
const LABEL_INACTIVE = 'font-normal text-[var(--color-neutral-400)]';

const TABS: { id: AuthMethod; i18nKey: string }[] = [
  { id: 'face', i18nKey: 'auth_type.face' },
  { id: 'palm', i18nKey: 'auth_type.palm' },
];

export default function AuthMethodTabs({ value, onChange, className }: AuthMethodTabsProps) {
  const { t } = useTranslation();

  return (
    <div className={['flex items-center gap-5', className ?? ''].join(' ')} role="tablist">
      {TABS.map((tab) => {
        const isActive = tab.id === value;
        const Icon = tab.id === 'face' ? FaceIdIcon : FingerprintIcon;
        return (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={isActive}
            onClick={() => onChange(tab.id)}
            className={[TAB_BASE, isActive ? TAB_ACTIVE : ''].join(' ')}
          >
            {/* 아이콘은 img 기반이라 색상 토큰이 적용되지 않음 → 미선택은 opacity로 근사 */}
            <Icon size={32} className={isActive ? '' : 'opacity-40'} />
            <span className={[LABEL_BASE, isActive ? LABEL_ACTIVE : LABEL_INACTIVE].join(' ')}>
              {t(tab.i18nKey)}
            </span>
          </button>
        );
      })}
    </div>
  );
}

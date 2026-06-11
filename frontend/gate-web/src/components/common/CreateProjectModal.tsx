/**
 * CreateProjectModal — 프로젝트 생성 우측 패널
 *
 * Figma node 1764:4247 기반 (이름 + 사용 목적 2필드로 단순화)
 *  ※ projectType/projectModuleType은 UI에서 제거되어 기본값(STANDARD/FACE)으로 생성됨
 */

import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';

export interface CreateProjectData {
  name:              string;
  goal:              string;
  projectType:       'STANDARD' | 'EXTERNAL';
  packageKey:        string;
  projectModuleType: 'FACE' | 'PALM';
}

interface Props {
  isOpen:    boolean;
  onClose:   () => void;
  onSubmit:  (data: CreateProjectData) => void;
  loading?:  boolean;
  error?:    string | null;
}

/* ── 아이콘 ── */
const FlagIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--color-neutral-400)"
    strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z" />
    <line x1="4" y1="22" x2="4" y2="15" />
  </svg>
);
const HashIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--color-neutral-400)"
    strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <line x1="4" y1="9" x2="20" y2="9" /><line x1="4" y1="15" x2="20" y2="15" />
    <line x1="10" y1="3" x2="8" y2="21" /><line x1="16" y1="3" x2="14" y2="21" />
  </svg>
);

/* ── 스타일 상수 ── */
const FIELD_LABEL = 'text-[14px] font-semibold text-[var(--color-neutral-700)] tracking-[-0.35px] leading-[1.4]';
const INPUT_FRAME = [
  'flex items-center gap-2 w-full h-[56px] px-3',
  'bg-white border border-[var(--color-border-default)] rounded-[var(--radius-xs)]',
  'focus-within:border-[var(--color-border-focus)] transition-colors',
].join(' ');
const INPUT_FIELD = [
  'flex-1 bg-transparent outline-none',
  'text-[16px] font-medium text-[var(--color-text-primary)] tracking-[-0.4px] leading-[1.4]',
  'placeholder:text-[var(--color-neutral-400)]',
].join(' ');

/* ── 메인 컴포넌트 (열릴 때만 내부 패널을 마운트 → 상태 자동 초기화) ── */
export default function CreateProjectModal({ isOpen, ...rest }: Props) {
  if (!isOpen) return null;
  return <CreateProjectPanel {...rest} />;
}

function CreateProjectPanel({ onClose, onSubmit, loading = false, error }: Omit<Props, 'isOpen'>) {
  const { t } = useTranslation();
  const [name, setName] = useState('');
  const [goal, setGoal] = useState('');

  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [onClose]);

  const canSubmit = name.trim().length > 0 && !loading;

  const handleSubmit = () => {
    if (!canSubmit) return;
    onSubmit({
      name: name.trim(),
      goal: goal.trim(),
      projectType: 'STANDARD',
      packageKey: '',
      projectModuleType: 'FACE',
    });
  };

  return (
    <div className="fixed inset-0 z-[var(--z-modal)] flex">
      <div className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]" onClick={onClose} />

      <div
        className={[
          'absolute top-[26px] right-[18px]',
          'w-[744px] max-w-[calc(100vw-36px)]',
          'h-[calc(100vh-53px)]',
          'bg-white rounded-[16px]',
          'flex flex-col overflow-hidden',
          'shadow-[var(--shadow-xl)]',
        ].join(' ')}
        onClick={(e) => e.stopPropagation()}
      >
        {/* 헤더 */}
        <div className="flex-shrink-0 px-9 pt-10 pb-0">
          <div className="flex flex-col gap-1">
            <h2 className="text-[24px] font-bold text-[var(--color-text-primary)] tracking-[-0.025px] leading-[1.4]">
              {t('projects.create_title')}
            </h2>
            <p className="text-[18px] font-normal text-[#464A4D] tracking-[-0.025px] leading-[1.8]">
              {t('projects.create_subtitle')}
            </p>
          </div>
        </div>

        {/* 폼 */}
        <div className="flex-1 overflow-y-auto px-9 py-10">
          <div className="flex flex-col gap-8">

            {/* 1. 프로젝트 이름 (필수) */}
            <div className="flex flex-col gap-2">
              <label className={FIELD_LABEL}>
                {t('projects.name_label')} <span className="text-[var(--color-entry)]">*</span>
              </label>
              <div className={INPUT_FRAME}>
                <FlagIcon />
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder={t('projects.name_placeholder')}
                  className={INPUT_FIELD}
                />
              </div>
            </div>

            {/* 2. 사용 목적 */}
            <div className="flex flex-col gap-2">
              <label className={FIELD_LABEL}>{t('projects.goal_label')}</label>
              <div className={INPUT_FRAME}>
                <HashIcon />
                <input
                  type="text"
                  value={goal}
                  onChange={(e) => setGoal(e.target.value)}
                  placeholder={t('projects.goal_placeholder')}
                  className={INPUT_FIELD}
                />
              </div>
            </div>

          </div>
        </div>

        {/* 하단 버튼 */}
        <div className="flex-shrink-0 px-9 pb-9 flex flex-col gap-3.5">
          {error && (
            <p className="text-[14px] font-medium text-[var(--color-entry)] tracking-[-0.35px] leading-[1.4] text-center">
              {error}
            </p>
          )}
          <button
            type="button"
            onClick={handleSubmit}
            disabled={!canSubmit}
            className={[
              'w-full h-12 rounded-[var(--radius-xs)] text-[18px] font-bold tracking-[-0.025px]',
              'leading-[24px] transition-colors',
              canSubmit
                ? 'bg-[var(--color-link-blue)] hover:bg-[var(--color-link-blue-hover)] text-white'
                : 'bg-[var(--btn-disabled-bg)] text-[var(--btn-disabled-text)] cursor-not-allowed',
            ].join(' ')}
          >
            {loading ? '...' : t('projects.create_btn')}
          </button>
          <button
            type="button"
            onClick={onClose}
            className={[
              'w-full h-12 rounded-[var(--radius-xs)]',
              'text-[18px] font-bold text-[var(--color-neutral-600)] tracking-[-0.025px] leading-[24px]',
              'bg-[var(--color-gray-bg)] hover:bg-[var(--color-neutral-100)] transition-colors',
            ].join(' ')}
          >
            {t('common.close')}
          </button>
        </div>
      </div>
    </div>
  );
}

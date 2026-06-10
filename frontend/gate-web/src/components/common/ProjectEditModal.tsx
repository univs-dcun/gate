import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';

export interface ProjectEditData {
  projectName: string;
  purpose:     string;
}

interface ProjectEditModalProps {
  isOpen:       boolean;
  onClose:      () => void;
  onSubmit:     (data: ProjectEditData) => void;
  loading?:     boolean;
  initialData?: ProjectEditData;
}

/* ── 스타일 상수 ── */
const INPUT_BASE = [
  'w-full h-[56px] bg-white border border-[#e8eef2] rounded-[8px]',
  'px-4 text-[16px] font-medium text-[#1e293b] tracking-[-0.4px]',
  'placeholder:text-[#94a3b8] outline-none',
  'focus:border-[#006fff] transition-colors',
].join(' ');

const LABEL_PRIMARY   = 'text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]';
const LABEL_SECONDARY = 'text-[13px] text-[#64748b] tracking-[-0.325px] leading-[1.4]';

/* ── 입력 필드 서브컴포넌트 ── */
function FormField({
  label, labelStyle, value, onChange, placeholder,
}: {
  label: string; labelStyle: string; value: string;
  onChange: (v: string) => void; placeholder?: string;
}) {
  return (
    <div className="flex flex-col gap-[8px]">
      <label className={labelStyle}>{label}</label>
      <input
        type="text"
        className={INPUT_BASE}
        placeholder={placeholder ?? label}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
    </div>
  );
}

/* ── 프로젝트 정보 수정 모달 ── */
function ProjectEditModal({ isOpen, onClose, onSubmit, loading, initialData }: ProjectEditModalProps) {
  const { t } = useTranslation();

  const [projectName, setProjectName] = useState('');
  const [purpose,     setPurpose]     = useState('');

  /* 초기값 동기화 */
  useEffect(() => {
    if (!isOpen) return;
    setProjectName(initialData?.projectName ?? '');
    setPurpose(    initialData?.purpose     ?? '');
  }, [isOpen, initialData]);

  /* ESC 키 닫기 */
  useEffect(() => {
    if (!isOpen) return;
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [isOpen, onClose]);

  const isDirty = (() => {
    if (!initialData) return [projectName, purpose].some(Boolean);
    return projectName !== initialData.projectName || purpose !== initialData.purpose;
  })();

  const handleSubmit = () => {
    if (!isDirty || loading) return;
    onSubmit({ projectName, purpose });
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[var(--z-modal)]" aria-modal="true" role="dialog">
      {/* 배경 오버레이 */}
      <div
        className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]"
        onClick={onClose}
      />

      {/* 모달 패널 — 우측 24px, 상하 24px 동일 마진으로 전체 높이 */}
      <div className={[
        'absolute right-6 top-6 bottom-6',
        'w-[744px] overflow-y-auto',
        'bg-white rounded-[34px] px-9 py-[52px]',
        'flex flex-col justify-between',
      ].join(' ')}>

        {/* 콘텐츠 영역 */}
        <div className="flex flex-col gap-[20px]">

          {/* 헤더 */}
          <div className="flex flex-col gap-[4px]">
            <span className="text-[18px] font-semibold text-[#8a58ff] tracking-[-0.45px] leading-[1.4]">
              {t('settings.project_info_title')}
            </span>
            <h2 className="text-[24px] font-bold text-[#1e293b] tracking-[-0.025em] leading-[1.4]">
              {t('settings.edit_project_subtitle')}
            </h2>
          </div>

          {/* 입력 필드 */}
          <div className="flex flex-col gap-[16px]">
            <FormField
              label={t('settings.project_name')}
              labelStyle={LABEL_PRIMARY}
              value={projectName}
              onChange={setProjectName}
              placeholder={t('settings.project_name_placeholder')}
            />
            <FormField
              label={t('settings.purpose')}
              labelStyle={LABEL_SECONDARY}
              value={purpose}
              onChange={setPurpose}
              placeholder={t('settings.purpose_placeholder')}
            />
          </div>
        </div>

        {/* 액션 버튼 */}
        <div className="flex flex-col gap-[12px] mt-8">
          <button
            onClick={handleSubmit}
            disabled={!isDirty || loading}
            className={[
              'w-full h-[48px] rounded-[8px]',
              'text-[18px] font-semibold tracking-[-0.45px] leading-[1.4] transition-colors',
              isDirty && !loading
                ? 'bg-[#006fff] text-white hover:opacity-90'
                : 'bg-[#cbd5e1] text-[#64748b] cursor-not-allowed',
            ].join(' ')}
          >
            {loading ? t('common.loading') : t('common.edit')}
          </button>
          <button
            onClick={onClose}
            className="w-full h-[48px] rounded-[8px] bg-[#f1f5f9] text-[#64748b] text-[18px] font-semibold tracking-[-0.45px] leading-[1.4] hover:bg-[#e2e8f0] transition-colors"
          >
            {t('common.close')}
          </button>
        </div>
      </div>
    </div>
  );
}

export default ProjectEditModal;

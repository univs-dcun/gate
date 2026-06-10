import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';

export interface AccountEditData {
  name:        string;
  contact:     string;
  companyName: string;
  bizRegNo:    string;
  mainService: string;
  bizType:     string;
  employeeCount: string;
}

interface AccountEditModalProps {
  isOpen:       boolean;
  onClose:      () => void;
  onSubmit:     (data: AccountEditData) => void;
  loading?:     boolean;
  initialData?: Partial<AccountEditData>;
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

/* ── 계정 정보 수정 모달 ── */
function AccountEditModal({ isOpen, onClose, onSubmit, loading, initialData }: AccountEditModalProps) {
  const { t } = useTranslation();

  const [name,          setName]          = useState('');
  const [contact,       setContact]       = useState('');
  const [companyName,   setCompanyName]   = useState('');
  const [bizRegNo,      setBizRegNo]      = useState('');
  const [mainService,   setMainService]   = useState('');
  const [bizType,       setBizType]       = useState('');
  const [employeeCount, setEmployeeCount] = useState('');

  /* 모달 열릴 때 초기 데이터로 필드 채우기 */
  useEffect(() => {
    if (isOpen && initialData) {
      setName(initialData.name ?? '');
      setContact(initialData.contact ?? '');
      setCompanyName(initialData.companyName ?? '');
      setBizRegNo(initialData.bizRegNo ?? '');
      setMainService(initialData.mainService ?? '');
      setBizType(initialData.bizType ?? '');
      setEmployeeCount(initialData.employeeCount ?? '');
    }
  }, [isOpen]); // eslint-disable-line react-hooks/exhaustive-deps

  /* ESC 키 닫기 */
  useEffect(() => {
    if (!isOpen) return;
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [isOpen, onClose]);

  const isDirty = [name, contact, companyName, bizRegNo, mainService, bizType, employeeCount].some(Boolean);

  const handleSubmit = () => {
    if (!isDirty || loading) return;
    onSubmit({ name, contact, companyName, bizRegNo, mainService, bizType, employeeCount });
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
            <span className="text-[18px] font-semibold text-[#006fff] tracking-[-0.45px] leading-[1.4]">
              {t('settings.account_info')}
            </span>
            <h2 className="text-[24px] font-bold text-[#1e293b] tracking-[-0.025em] leading-[1.4]">
              {t('settings.edit_account_subtitle')}
            </h2>
          </div>

          {/* 입력 필드 목록 */}
          <div className="flex flex-col gap-[16px]">
            <FormField
              label={t('settings.manager_name')}
              labelStyle={LABEL_PRIMARY}
              value={name}
              onChange={setName}
              placeholder={t('settings.manager_name_placeholder')}
            />
            <FormField
              label={t('settings.contact')}
              labelStyle={LABEL_PRIMARY}
              value={contact}
              onChange={setContact}
              placeholder={t('settings.contact_placeholder')}
            />
            <FormField
              label={t('settings.company_name')}
              labelStyle={LABEL_PRIMARY}
              value={companyName}
              onChange={setCompanyName}
              placeholder={t('settings.company_name_placeholder')}
            />
            <FormField
              label={t('settings.biz_reg_no')}
              labelStyle={LABEL_SECONDARY}
              value={bizRegNo}
              onChange={setBizRegNo}
              placeholder={t('settings.biz_reg_no_placeholder')}
            />
            <FormField
              label={t('settings.main_service')}
              labelStyle={LABEL_SECONDARY}
              value={mainService}
              onChange={setMainService}
              placeholder={t('settings.main_service_placeholder')}
            />
            <FormField
              label={t('settings.biz_type')}
              labelStyle={LABEL_SECONDARY}
              value={bizType}
              onChange={setBizType}
              placeholder={t('settings.biz_type_placeholder')}
            />
            <FormField
              label={t('settings.employee_count')}
              labelStyle={LABEL_SECONDARY}
              value={employeeCount}
              onChange={setEmployeeCount}
              placeholder={t('settings.employee_count_placeholder')}
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

export default AccountEditModal;

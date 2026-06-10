import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';

interface PasswordChangeModalProps {
  isOpen:   boolean;
  onClose:  () => void;
  onSubmit: (password: string, newPassword: string) => void;
  loading?: boolean;
  error?:   string | null;
}

/* ── 스타일 상수 ── */
const INPUT_BASE = [
  'w-full h-[56px] bg-white border border-[#e8eef2] rounded-[8px]',
  'px-4 text-[16px] font-medium text-[#1e293b] tracking-[-0.4px]',
  'placeholder:text-[#94a3b8] outline-none',
  'focus:border-[#006fff] transition-colors',
].join(' ');

const INPUT_ERROR = [
  'w-full h-[56px] bg-white border border-[#ef4444] rounded-[8px]',
  'px-4 text-[16px] font-medium text-[#1e293b] tracking-[-0.4px]',
  'placeholder:text-[#94a3b8] outline-none',
  'focus:border-[#ef4444] transition-colors',
].join(' ');

const LABEL_PRIMARY = 'text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]';

/* ── 비밀번호 입력 필드 ── */
function PasswordField({
  label, value, onChange, placeholder, error,
}: {
  label: string; value: string;
  onChange: (v: string) => void; placeholder?: string; error?: string;
}) {
  const [show, setShow] = useState(false);
  return (
    <div className="flex flex-col gap-[8px]">
      <label className={LABEL_PRIMARY}>{label}</label>
      <div className="relative">
        <input
          type={show ? 'text' : 'password'}
          className={error ? INPUT_ERROR : INPUT_BASE}
          placeholder={placeholder ?? label}
          value={value}
          onChange={(e) => onChange(e.target.value)}
        />
        <button
          type="button"
          tabIndex={-1}
          onClick={() => setShow(v => !v)}
          className="absolute right-4 top-1/2 -translate-y-1/2 text-[#94a3b8] hover:text-[#64748b] transition-colors"
        >
          {show ? (
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
              <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
              <line x1="1" y1="1" x2="23" y2="23" />
            </svg>
          ) : (
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
          )}
        </button>
      </div>
      {error && (
        <span className="text-[13px] text-[#ef4444] tracking-[-0.325px]">{error}</span>
      )}
    </div>
  );
}

/* ── 비밀번호 변경 모달 ── */
function PasswordChangeModal({ isOpen, onClose, onSubmit, loading, error }: PasswordChangeModalProps) {
  const { t } = useTranslation();

  const [currentPassword,  setCurrentPassword]  = useState('');
  const [newPassword,      setNewPassword]      = useState('');
  const [passwordConfirm,  setPasswordConfirm]  = useState('');
  const [validationError,  setValidationError]  = useState<string | null>(null);
  const [confirmError,     setConfirmError]     = useState<string | null>(null);

  /* 모달 열릴 때 초기화 */
  useEffect(() => {
    if (isOpen) {
      setCurrentPassword('');
      setNewPassword('');
      setPasswordConfirm('');
      setValidationError(null);
      setConfirmError(null);
    }
  }, [isOpen]);

  /* ESC 키 닫기 */
  useEffect(() => {
    if (!isOpen) return;
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [isOpen, onClose]);

  const validate = (): boolean => {
    setValidationError(null);
    setConfirmError(null);

    if (newPassword.length < 8) {
      setValidationError(t('settings.password_change_invalid_length'));
      return false;
    }
    if (newPassword !== passwordConfirm) {
      setConfirmError(t('settings.password_change_confirm_mismatch'));
      return false;
    }
    return true;
  };

  const handleSubmit = () => {
    if (loading) return;
    if (!validate()) return;
    onSubmit(currentPassword, newPassword);
  };

  const isReady = currentPassword.length > 0 && newPassword.length > 0 && passwordConfirm.length > 0;

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[var(--z-modal)]" aria-modal="true" role="dialog">
      {/* 배경 오버레이 */}
      <div
        className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]"
        onClick={onClose}
      />

      {/* 모달 패널 — AccountEditModal과 동일 위치/크기 */}
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
              {t('settings.password_change_subtitle')}
            </h2>
          </div>

          {/* 입력 필드 */}
          <div className="flex flex-col gap-[16px]">
            <PasswordField
              label={t('settings.password_change_current')}
              value={currentPassword}
              onChange={setCurrentPassword}
              placeholder={t('settings.password_change_current_placeholder')}
            />
            <PasswordField
              label={t('settings.password_change_new')}
              value={newPassword}
              onChange={(v) => { setNewPassword(v); setValidationError(null); }}
              placeholder={t('settings.password_change_new_placeholder')}
              error={validationError ?? undefined}
            />
            <PasswordField
              label={t('settings.password_change_confirm')}
              value={passwordConfirm}
              onChange={(v) => { setPasswordConfirm(v); setConfirmError(null); }}
              placeholder={t('settings.password_change_confirm_placeholder')}
              error={confirmError ?? undefined}
            />
          </div>

          {/* API 오류 메시지 */}
          {error && (
            <div className="px-4 py-3 rounded-[8px] bg-[#fef2f2] border border-[#fecaca]">
              <p className="text-[14px] text-[#ef4444] tracking-[-0.35px]">{error}</p>
            </div>
          )}
        </div>

        {/* 액션 버튼 */}
        <div className="flex flex-col gap-[12px] mt-8">
          <button
            onClick={handleSubmit}
            disabled={!isReady || loading}
            className={[
              'w-full h-[48px] rounded-[8px]',
              'text-[18px] font-semibold tracking-[-0.45px] leading-[1.4] transition-colors',
              isReady && !loading
                ? 'bg-[#006fff] text-white hover:opacity-90'
                : 'bg-[#cbd5e1] text-[#64748b] cursor-not-allowed',
            ].join(' ')}
          >
            {loading ? t('common.loading') : t('settings.password_change_submit')}
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

export default PasswordChangeModal;

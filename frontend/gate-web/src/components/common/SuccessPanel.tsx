import { useTranslation } from 'react-i18next';

export interface SuccessPanelField {
  label: string;
  value: string;
}

interface SuccessPanelProps {
  accentColor: string;
  title:       string;
  subtitle:    string;
  fields:      SuccessPanelField[];
  onClose:     () => void;
}

const CheckCircleIcon = () => (
  <svg width="28" height="28" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <polyline points="20 6 9 17 4 12" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

export default function SuccessPanel({ accentColor, title, subtitle, fields, onClose }: SuccessPanelProps) {
  const { t } = useTranslation();
  return (
    <div className="fixed inset-0 z-[var(--z-modal)]" aria-modal="true" role="dialog">
      <div className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]" onClick={onClose} />

      <div className="absolute right-6 top-6 bottom-6 w-[744px] overflow-y-auto bg-white rounded-[34px] px-9 py-[52px] flex flex-col justify-between">
        <div className="flex flex-col items-center gap-[32px]">
          <div
            className="w-[52px] h-[52px] rounded-full flex items-center justify-center flex-shrink-0"
            style={{ backgroundColor: accentColor }}
          >
            <CheckCircleIcon />
          </div>

          <div className="flex flex-col items-center gap-[8px] text-center">
            <h2 className="text-[26px] font-semibold text-[#1e293b] tracking-[-0.65px]">{title}</h2>
            <p className="text-[14px] text-[#64748b] tracking-[-0.35px]">{subtitle}</p>
          </div>

          {fields.length > 0 && (
            <div className="w-full flex flex-col">
              {fields.map(({ label, value }, idx) => (
                <div key={label}>
                  {idx > 0 && <div className="h-px bg-[#e2e8f0]" />}
                  <div className="flex items-center justify-between py-[14px]">
                    <span className="text-[14px] font-medium text-[#94a3b8] tracking-[-0.35px]">{label}</span>
                    <span className="text-[14px] text-[#1e293b] tracking-[-0.35px] text-right max-w-[55%] break-words">
                      {value || '—'}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <button
          onClick={onClose}
          className="w-full h-[48px] text-white text-[18px] font-semibold rounded-[8px] tracking-[-0.45px] hover:opacity-90 transition-opacity"
          style={{ backgroundColor: accentColor }}
        >
          {t('common.confirm')}
        </button>
      </div>
    </div>
  );
}

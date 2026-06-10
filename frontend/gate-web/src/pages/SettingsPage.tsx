import { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { DashboardLayout, LangSelector } from '@/components/layout';
import { useProjectContext } from '@/contexts/ProjectContext';
import { Alert } from '@/components/ui';
import { AccountEditModal, ProjectEditModal, PasswordChangeModal, SuccessPanel } from '@/components/common';
import type { AccountEditData, ProjectEditData } from '@/components/common';
import { changePassword } from '@/services/auth';
import { getProjectSettings, updateLivenessSettings, updateProject, updateConsentSettings, getConsentLogs, isLivenessEnabled } from '@/services/project';
import type { LivenessOperation } from '@/services/project';
import type { Project, ConsentLog } from '@/services/project';
import { getCompanyInfo, upsertCompanyInfo } from '@/services/account';
import type { CompanyInfo } from '@/services/account';

/* ── 스타일 상수 ── */
const SECTION_TITLE = 'text-[20px] font-semibold text-[#334155] tracking-[-0.5px] leading-[1.4]';
const FIELD_LABEL   = 'text-[13px] text-[#64748b] tracking-[-0.325px] leading-[1.4]';
const CARD = [
  'flex flex-col gap-5 p-6',
  'bg-[var(--card-bg)] border border-[var(--card-border)]',
  'rounded-[var(--card-radius)] shadow-[var(--card-shadow)]',
].join(' ');
const BADGE_ACCOUNT = [
  'inline-flex items-center px-[8px] py-[4px]',
  'bg-[#eff9ff] rounded-[8px] text-[#006fff] text-[13px] tracking-[-0.325px] leading-[1.4]',
].join(' ');
const BADGE_PROJECT = [
  'inline-flex items-center px-[8px] py-[4px]',
  'bg-[#f5f2ff] rounded-[8px] text-[#8a58ff] text-[13px] tracking-[-0.325px] leading-[1.4]',
].join(' ');

/* ── 아이콘 ── */
const AccountInfoIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="#64748b" aria-hidden="true">
    <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
  </svg>
);

const GlobeSettingsIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#64748b" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <circle cx="12" cy="12" r="10" />
    <path d="M2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
  </svg>
);

const ActivityIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#64748b" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
  </svg>
);

const ProjectInfoIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#64748b" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" />
    <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" />
  </svg>
);


const LockIcon = () => (
  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
    <path d="M7 11V7a5 5 0 0 1 10 0v4" />
  </svg>
);

const ShieldIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
  </svg>
);

const InfoCircleIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <circle cx="12" cy="12" r="10" />
    <line x1="12" y1="8" x2="12" y2="8" strokeWidth="2.5" />
    <line x1="12" y1="12" x2="12" y2="16" />
  </svg>
);

/* ── 서브컴포넌트 ── */
function SectionHeader({ icon, title, badge, badgeStyle }: { icon?: ReactNode; title: string; badge: string; badgeStyle: string }) {
  return (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-[8px]">
        {icon && <span className="flex items-center text-[#64748b]">{icon}</span>}
        <h2 className={SECTION_TITLE}>{title}</h2>
      </div>
      <span className={badgeStyle}>{badge}</span>
    </div>
  );
}

/* 정보 수정 링크 버튼 (편집 아이콘 + 라벨 + chevron, 블루 링크) */
function EditLinkBtn({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="flex items-center gap-1 text-[14px] font-semibold text-[var(--color-link-blue)] tracking-[-0.35px] hover:opacity-75 transition-opacity whitespace-nowrap">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
        <path d="M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4z" />
      </svg>
      {label}
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <polyline points="9 18 15 12 9 6" />
      </svg>
    </button>
  );
}

/* 라이브니스 사용 여부 패널 (얼굴/손바닥) — Figma node 1740:4220 */
function LivenessPanel({ variant, modules }: { variant: 'face' | 'palm'; modules: { label: string; checked: boolean; onChange: (v: boolean) => void }[] }) {
  const { t } = useTranslation();
  const isPalm = variant === 'palm';
  const color = isPalm ? 'var(--color-purple)' : 'var(--color-link-blue)';
  return (
    <div className="flex-1 min-w-0 flex flex-col gap-4 p-4 bg-[var(--color-neutral-50-bg)] rounded-[12px]">
      <div className="flex flex-col gap-3 w-full">
        <div className="flex items-center gap-2">
          {/* 얼굴=블루(#006FFF) / 손바닥=퍼플(#8A58FF) — 디자인 시스템 컬러 아이콘 */}
          {isPalm
            ? <img src="/icons/ic-authbadge-palm.svg" alt="" className="block w-5 h-5 object-contain shrink-0" aria-hidden />
            : <img src="/icons/ic-authbadge-face.svg" alt="" className="block w-[18px] h-[18px] object-contain shrink-0" aria-hidden />}
          <p className="text-[14px] font-semibold tracking-[-0.35px] leading-[20px]">
            <span style={{ color }}>{isPalm ? t('auth_type.palm_short') : t('auth_type.face_short')}</span>
            <span className="text-[var(--color-neutral-700)]"> {t('settings.liveness_usage_by')}</span>
          </p>
        </div>
        <div className="h-px bg-[var(--color-neutral-200)]" />
      </div>
      <div className="flex flex-col gap-5">
        {modules.map(({ label, checked, onChange }) => (
          <div key={label} className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2 min-w-0">
              <span className="text-[14px] font-semibold text-[var(--color-neutral-700)] tracking-[-0.35px] leading-[20px] whitespace-nowrap">{label}</span>
              <span className={[
                'inline-flex items-center px-2 py-1 rounded-[8px] border text-[13px] font-normal leading-[18px] whitespace-nowrap',
                checked
                  ? 'bg-[var(--color-primary-100)] border-[var(--color-primary-300)] text-[var(--color-link-blue)]'
                  : 'bg-white border-[var(--color-neutral-300)] text-[var(--color-neutral-700)]',
              ].join(' ')}>
                {checked ? t('settings.used') : t('settings.not_used')}
              </span>
            </div>
            <Toggle checked={checked} onChange={onChange} />
          </div>
        ))}
      </div>
    </div>
  );
}

/* ── 개인정보 동의 확인 모달 ── */
function ConsentConfirmModal({ activating, onConfirm, onCancel }: { activating: boolean; onConfirm: () => void; onCancel: () => void }) {
  const { t } = useTranslation();

  const InfoBoxRow = ({ labelKey, children }: { labelKey: string; children: ReactNode }) => (
    <div className="flex gap-6 items-start">
      <span className="flex-shrink-0 inline-flex items-center justify-center px-3 py-2 border border-[#94a3b8] rounded-[8px] bg-white text-[15px] font-semibold text-[#475569] whitespace-nowrap">
        {t(labelKey)}
      </span>
      <div className="flex flex-col gap-3 justify-center">{children}</div>
    </div>
  );

  return createPortal(
    <div className="fixed inset-0 z-[var(--z-modal)]" aria-modal="true" role="dialog">
      <div className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]" onClick={onCancel} />
      <div
        className="absolute right-6 top-6 bottom-6 w-[744px] overflow-y-auto bg-white rounded-[34px] px-9 py-[52px] flex flex-col justify-between gap-[84px]"
        onClick={e => e.stopPropagation()}
      >
        {/* 상단 */}
        <div className="flex flex-col gap-[84px] items-center">
          {/* 타이틀 영역 */}
          <div className="flex flex-col gap-3 items-center w-full">
            {/* 경고 아이콘 */}
            <div className="w-[78px] h-[73px] flex items-center justify-center">
              <div className="w-16 h-16 rounded-full bg-[#FFF8E1] flex items-center justify-center">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="#F59E0B">
                  <path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z" />
                </svg>
              </div>
            </div>
            {/* 제목 */}
            <p className="text-[20px] font-semibold text-[#1e293b] tracking-[-0.5px] text-center leading-[1.6]">
              {t('settings.consent_dialog_prefix')}
              <span className={activating ? 'text-[#ef4444]' : 'text-[#006fff]'}>
                {t(activating ? 'settings.consent_activate_keyword' : 'settings.consent_deactivate_keyword')}
              </span>
              {t('settings.consent_dialog_suffix')}
            </p>
            {/* 부제 */}
            {activating ? (
              <p className="text-[16px] font-semibold text-[#64748b] tracking-[-0.4px] text-center leading-[1.5]">
                {t('settings.consent_activate_subtitle')}
              </p>
            ) : (
              <div className="flex flex-col gap-1 items-center">
                <p className="text-[16px] font-semibold text-[#64748b] tracking-[-0.4px] text-center leading-[1.5]">
                  {t('settings.consent_deactivate_subtitle_1')}
                </p>
                <p className="text-[16px] font-semibold text-[#0fb981] tracking-[-0.4px] text-center leading-[1.5] bg-[#f4fcfb] px-2.5 py-1">
                  {t('settings.consent_deactivate_subtitle_2')}
                </p>
              </div>
            )}
          </div>

          {/* 정보 박스 */}
          <div className="w-full bg-[#f8f9fb] rounded-[12px] px-8 py-6 flex flex-col gap-[18px]">
            <p className="text-[16px] font-semibold text-[#334155] tracking-[-0.4px] leading-[1.5]">
              {t(activating ? 'settings.consent_activate_info_label' : 'settings.consent_deactivate_info_label')}
            </p>
            <div className="flex flex-col gap-5">
              <InfoBoxRow labelKey={activating ? 'settings.consent_expose_info_label' : 'settings.consent_hide_info_label'}>
                <p className="text-[16px] font-semibold text-[#334155] tracking-[-0.4px] leading-[1.5]">
                  {t(activating ? 'settings.consent_expose_info_content' : 'settings.consent_hide_info_content')}
                </p>
              </InfoBoxRow>
              <div className="h-px bg-[#e2e8f0]" />
              <InfoBoxRow labelKey="settings.consent_apply_screen_label">
                <p className="text-[16px] font-semibold text-[#334155] tracking-[-0.4px] leading-[1.5]">
                  {t('settings.consent_apply_screens')}
                </p>
                <ul className="list-disc pl-5 flex flex-col gap-3">
                  <li className="text-[16px] text-[#334155] leading-[1.5]">
                    <span>{t(activating ? 'settings.consent_activate_bullet_1_text' : 'settings.consent_deactivate_bullet_1_text')} </span>
                    <span className="font-semibold">{t(activating ? 'settings.consent_check_possible' : 'settings.consent_check_impossible')}</span>
                  </li>
                  <li className="text-[16px] text-[#334155] leading-[1.5]">
                    <span>{t(activating ? 'settings.consent_activate_bullet_2_text' : 'settings.consent_deactivate_bullet_2_text')} </span>
                    <span className="font-semibold">{t(activating ? 'settings.consent_check_possible' : 'settings.consent_check_impossible')}</span>
                  </li>
                </ul>
              </InfoBoxRow>
            </div>
          </div>
        </div>

        {/* 하단 버튼 */}
        <div className="flex flex-col gap-5">
          {activating && (
            <div className="flex items-center justify-center gap-2">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#006fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="8" strokeWidth="2.5" /><line x1="12" y1="12" x2="12" y2="16" />
              </svg>
              <p className="text-[14px] font-semibold text-[#004399] tracking-[-0.35px]">
                {t('settings.consent_agree_note')}
              </p>
            </div>
          )}
          <div className="flex gap-3">
            <button
              onClick={onCancel}
              className="flex-1 py-4 border border-[#cbd5e1] rounded-[12px] text-[18px] font-semibold text-[#475569] tracking-[-0.45px] hover:bg-[#f8fafc] transition-colors"
            >
              {t(activating ? 'settings.consent_btn_cancel' : 'settings.consent_btn_keep')}
            </button>
            <button
              onClick={onConfirm}
              className="flex-1 py-4 bg-[#006fff] rounded-[12px] text-[18px] font-semibold text-white tracking-[-0.45px] hover:bg-[#005cd6] transition-colors"
            >
              {t(activating ? 'settings.consent_btn_agree_activate' : 'settings.consent_btn_deactivate')}
            </button>
          </div>
        </div>
      </div>
    </div>,
    document.body
  );
}

/* ── 동의 변경 이력 모달 ── */
function ConsentHistoryModal({ logs, isLoading, onClose }: { logs: ConsentLog[]; isLoading: boolean; onClose: () => void }) {
  const { t } = useTranslation();

  const ClockIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="10" />
      <polyline points="12 6 12 12 16 14" />
    </svg>
  );

  return createPortal(
    <div className="fixed inset-0 z-[var(--z-modal)]" aria-modal="true" role="dialog">
      <div className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]" onClick={onClose} />
      <div
        className="absolute right-6 top-6 bottom-6 w-[500px] bg-white rounded-[34px] px-9 py-10 flex flex-col gap-6 shadow-[var(--shadow-xl)]"
        onClick={e => e.stopPropagation()}
      >
        {/* 헤더 */}
        <div className="flex items-center justify-between flex-shrink-0">
          <h2 className="text-[24px] font-bold text-[#1e293b] tracking-[-0.025px] leading-[1.4]">
            {t('settings.consent_history_title')}
          </h2>
          <button
            onClick={onClose}
            className="w-9 h-9 flex items-center justify-center rounded-full hover:bg-[#f1f5f9] transition-colors text-[#94a3b8]"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        {/* 타임라인 */}
        <div className="flex-1 overflow-y-auto min-h-0">
          <div className="bg-[#f9fafc] rounded-[16px] px-5 py-6">
            {isLoading ? (
              <p className="text-[14px] text-[#94a3b8] text-center py-8">{t('common.loading')}</p>
            ) : logs.length === 0 ? (
              <p className="text-[14px] text-[#94a3b8] text-center py-8">{t('common.no_data')}</p>
            ) : (
              <div className="flex flex-col gap-3">
                {logs.map((log, idx) => {
                  const isLast = idx === logs.length - 1;
                  const isCurrent = idx === 0;
                  const dateStr = log.createdAt ? log.createdAt.slice(0, 19).replace('T', ' ') : '-';
                  return (
                    <div key={log.id} className="flex flex-col gap-2">
                      {/* 헤드: 아이콘 + 라벨 + 현재설정값 뱃지 */}
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                          <div className={['flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center', log.agreed ? 'bg-[#006fff]' : 'bg-[#ff4d4f]'].join(' ')}>
                            {log.agreed ? (
                              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                                <polyline points="20 6 9 17 4 12" />
                              </svg>
                            ) : (
                              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                              </svg>
                            )}
                          </div>
                          <span className="text-[18px] font-semibold text-[#334155] tracking-[-0.45px] leading-[1.4]">
                            {t(log.agreed ? 'settings.consent_agreed_label' : 'settings.consent_refused_label')}
                          </span>
                        </div>
                        {isCurrent && (
                          <span className="inline-flex items-center px-3 py-1 border border-[#cbd5e1] rounded-[8px] text-[12px] font-semibold text-[#334155] tracking-[-0.3px] whitespace-nowrap">
                            {t('settings.consent_current_badge')}
                          </span>
                        )}
                      </div>
                      {/* 수직선 + 시간 */}
                      {!isLast ? (
                        <div className="flex gap-7 pl-4">
                          <div className={['w-px h-11 flex-shrink-0', log.agreed ? 'bg-[#1890ff]' : 'bg-[#cbd5e1]'].join(' ')} />
                          <div className="flex items-center gap-1 text-[#475569]">
                            <ClockIcon />
                            <span className="text-[14px] tracking-[-0.35px]">{dateStr}</span>
                          </div>
                        </div>
                      ) : (
                        <div className="flex items-center gap-1 pl-11 text-[#64748b]">
                          <ClockIcon />
                          <span className="text-[14px] tracking-[-0.35px]">{dateStr}</span>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* 닫기 버튼 */}
        <button
          onClick={onClose}
          className="flex-shrink-0 w-full h-12 bg-[#f1f5f9] rounded-[8px] text-[18px] font-semibold text-[#475569] tracking-[-0.45px] hover:bg-[#e2e8f0] transition-colors"
        >
          {t('common.close')}
        </button>
      </div>
    </div>,
    document.body
  );
}

function ConsentHistoryModalWrapper({ projectId, onClose }: { projectId?: number; onClose: () => void }) {
  const { data, isLoading } = useQuery({
    queryKey:  ['consent-logs', projectId],
    queryFn:   () => getConsentLogs(projectId!).then(r => r.data.data.contents),
    enabled:   !!projectId,
    staleTime: 0,
  });
  return <ConsentHistoryModal logs={data ?? []} isLoading={isLoading} onClose={onClose} />;
}

function ReadOnlyField({ label, value, placeholder }: { label: string; value?: string; placeholder?: string }) {
  return (
    <div className="flex flex-col gap-[4px]">
      <span className={FIELD_LABEL}>{label}</span>
      <span className={[
        'h-[24px] flex items-center text-[14px] font-medium tracking-[-0.35px] truncate',
        value ? 'text-[#1e293b]' : 'text-[#cbd5e1]',
      ].join(' ')}>
        {value || placeholder || '—'}
      </span>
    </div>
  );
}

function Toggle({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className={[
        'relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full',
        'transition-colors duration-200 focus:outline-none',
        checked ? 'bg-[#006fff]' : 'bg-[#cbd5e1]',
      ].join(' ')}
    >
      <span className={[
        'inline-block h-5 w-5 transform rounded-full bg-white shadow-md',
        'transition-transform duration-200 self-center',
        checked ? 'translate-x-[22px]' : 'translate-x-[2px]',
      ].join(' ')} />
    </button>
  );
}

/* ── 성공 모달 ── */
function PasswordChangeSuccessModal({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  return (
    <SuccessPanel
      accentColor="#006fff"
      title={t('settings.password_change_success_title')}
      subtitle={t('settings.password_change_success_subtitle')}
      fields={[]}
      onClose={onClose}
    />
  );
}

function AccountEditSuccessModal({ data, onClose }: { data: CompanyInfo; onClose: () => void }) {
  const { t } = useTranslation();
  return (
    <SuccessPanel
      accentColor="#006fff"
      title={t('settings.account_edit_success_title')}
      subtitle={t('settings.account_edit_success_subtitle')}
      fields={[
        { label: t('settings.manager_name'),   value: data.managerName },
        { label: t('settings.contact'),        value: data.managerNumber },
        { label: t('settings.company_name'),   value: data.companyName },
        { label: t('settings.biz_reg_no'),     value: data.businessNumber },
        { label: t('settings.main_service'),   value: data.mainService },
        { label: t('settings.biz_type'),       value: data.businessType },
        { label: t('settings.employee_count'), value: data.employeeCount },
      ]}
      onClose={onClose}
    />
  );
}

function ProjectEditSuccessModal({ data, onClose }: { data: Project; onClose: () => void }) {
  const { t } = useTranslation();
  return (
    <SuccessPanel
      accentColor="#006fff"
      title={t('settings.project_edit_success_title')}
      subtitle={t('settings.project_edit_success_subtitle')}
      fields={[
        { label: t('settings.project_name'), value: data.projectName },
        { label: t('settings.purpose'),      value: data.projectDescription },
      ]}
      onClose={onClose}
    />
  );
}

function formatConsentDate(iso: string): string {
  const d = new Date(iso);
  const y  = d.getFullYear();
  const mo = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${y}.${mo}.${dd} ${hh}:${mm}`;
}

/* ── 설정 페이지 ── */
function SettingsPage() {
  const { t } = useTranslation();
  const { selectedProject } = useProjectContext();
  const queryClient = useQueryClient();

  const userEmail  = localStorage.getItem('user_email') ?? '';
  const initials   = userEmail.slice(0, 2).toUpperCase() || 'ME';

  const [livenessRegister,    setLivenessRegister]    = useState(false);
  const [livenessVerify,      setLivenessVerify]      = useState(false);
  const [livenessVerifyImage, setLivenessVerifyImage] = useState(false);
  const [livenessMatch,       setLivenessMatch]       = useState(false);
  /* 손바닥 라이브니스 — 백엔드 작업 중이라 현재는 로컬 상태만(미영속). API 추가되면 settings에서 초기화 + persist 연결 */
  const [palmLivenessRegister, setPalmLivenessRegister] = useState(false);
  const [palmLivenessMatch,    setPalmLivenessMatch]    = useState(false);
  const [accountModalOpen,       setAccountModalOpen]       = useState(false);
  const [projectModalOpen,       setProjectModalOpen]       = useState(false);
  const [passwordModalOpen,      setPasswordModalOpen]      = useState(false);
  const [successData,            setSuccessData]            = useState<CompanyInfo | null>(null);
  const [projectSuccessData,     setProjectSuccessData]     = useState<Project | null>(null);
  const [passwordChangeSuccess,  setPasswordChangeSuccess]  = useState(false);
  const [passwordChangeError,    setPasswordChangeError]    = useState<string | null>(null);
  const [livenessToast,          setLivenessToast]          = useState(false);
  const [consentEnabled,         setConsentEnabled]         = useState(false);
  const [consentAgreedAt,        setConsentAgreedAt]        = useState<string | null>(null);
  const [consentConfirmPending,  setConsentConfirmPending]  = useState<boolean | null>(null);
  const [historyOpen,            setHistoryOpen]            = useState(false);
  const projectId = selectedProject?.id ? Number(selectedProject.id) : undefined;

  const { data: company } = useQuery({
    queryKey:  ['company'],
    queryFn:   () => getCompanyInfo().then(r => r.data.data),
    staleTime: 0,
  });

  const { data: settings } = useQuery({
    queryKey:  ['project-settings', projectId],
    queryFn:   () => getProjectSettings(projectId!).then(r => r.data.data),
    enabled:   !!projectId,
    staleTime: 0,
  });

  useEffect(() => {
    if (settings) {
      setLivenessRegister(isLivenessEnabled(settings, 'FACE', 'REGISTER'));
      setLivenessVerify(isLivenessEnabled(settings, 'FACE', 'VERIFY_ID'));
      setLivenessVerifyImage(isLivenessEnabled(settings, 'FACE', 'VERIFY_IMAGE'));
      setLivenessMatch(isLivenessEnabled(settings, 'FACE', 'IDENTIFY'));
      setPalmLivenessRegister(isLivenessEnabled(settings, 'PALM', 'REGISTER'));
      setPalmLivenessMatch(isLivenessEnabled(settings, 'PALM', 'IDENTIFY'));
      setConsentEnabled(settings.consentEnabled);
      setConsentAgreedAt(settings.consentAgreedAt ?? null);
    }
  }, [settings]);

  const { mutate: saveConsent } = useMutation({
    mutationFn: (enabled: boolean) =>
      updateConsentSettings(projectId!, enabled).then(r => r.data.data),
    onSuccess: (data) => {
      setConsentEnabled(data.consentEnabled);
      setConsentAgreedAt(data.consentAgreedAt ?? null);
      queryClient.setQueryData(['project-settings', projectId], (old: typeof settings) =>
        old ? { ...old, ...data } : data,
      );
    },
  });

  const { mutate: saveLiveness } = useMutation({
    mutationFn: (arg: { moduleType: 'FACE' | 'PALM'; settings: { operation: LivenessOperation; enabled: boolean }[] }) =>
      updateLivenessSettings(projectId!, arg.moduleType, arg.settings).then(r => r.data.data),
    onSuccess: (data) => {
      queryClient.setQueryData(['project-settings', projectId], data);
      setLivenessToast(true);
    },
  });

  const handleLivenessToggle = (register: boolean, verify: boolean, verifyImage: boolean, match: boolean) => {
    setLivenessRegister(register);
    setLivenessVerify(verify);
    setLivenessVerifyImage(verifyImage);
    setLivenessMatch(match);
    if (projectId) {
      saveLiveness({
        moduleType: 'FACE',
        settings: [
          { operation: 'REGISTER',     enabled: register },
          { operation: 'VERIFY_ID',    enabled: verify },
          { operation: 'VERIFY_IMAGE', enabled: verifyImage },
          { operation: 'IDENTIFY',     enabled: match },
        ],
      });
    }
  };

  /* 손바닥 라이브니스 — 1:1 인증이 없어 등록/1:N 매칭만. PALM 모듈로 독립 저장 */
  const handlePalmLivenessToggle = (register: boolean, match: boolean) => {
    setPalmLivenessRegister(register);
    setPalmLivenessMatch(match);
    if (projectId) {
      saveLiveness({
        moduleType: 'PALM',
        settings: [
          { operation: 'REGISTER', enabled: register },
          { operation: 'IDENTIFY', enabled: match },
        ],
      });
    }
  };


  const { mutate: saveCompany, isPending: isSavingCompany } = useMutation({
    mutationFn: (data: AccountEditData) =>
      upsertCompanyInfo({
        companyName:    data.companyName,
        businessNumber: data.bizRegNo,
        managerName:    data.name,
        managerNumber:  data.contact,
        mainService:    data.mainService,
        businessType:   data.bizType,
        employeeCount:  data.employeeCount,
      }).then(r => r.data.data),
    onSuccess: (data) => {
      queryClient.setQueryData(['company'], data);
      setAccountModalOpen(false);
      setSuccessData(data);
    },
  });

  const { mutate: saveProject, isPending: isSavingProject } = useMutation({
    mutationFn: (data: ProjectEditData) =>
      updateProject(projectId!, {
        projectName:        data.projectName,
        projectDescription: data.purpose,
      }).then(r => r.data.data),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      setProjectModalOpen(false);
      setProjectSuccessData(data);
    },
  });

  const { mutate: doChangePassword, isPending: isChangingPassword } = useMutation({
    mutationFn: ({ password, newPassword }: { password: string; newPassword: string }) => {
      const accountId = Number(localStorage.getItem('account_id'));
      return changePassword(accountId, password, newPassword);
    },
    onSuccess: () => {
      setPasswordModalOpen(false);
      setPasswordChangeError(null);
      setPasswordChangeSuccess(true);
    },
    onError: () => {
      setPasswordChangeError(t('settings.password_change_error'));
    },
  });

  /* 인증 방법 / 타입 조합 표시 */

  return (
    <DashboardLayout>
      <div className="flex flex-col gap-5">

        {/* 페이지 제목 */}
        <h1 className="text-[26px] font-semibold text-[#1e293b] tracking-[-0.65px] leading-[1.4]">
          {t('settings.title')}
        </h1>

        {/* 2컬럼 레이아웃 — 좌: 계정 공통 설정 / 우: 프로젝트별 설정 */}
        <div className="flex gap-5 items-start">

          {/* ── 좌측: 계정 정보 + 언어 (계정 공통 설정) ── */}
          <div className="w-[486px] flex-shrink-0 flex flex-col gap-5">

            {/* 계정 정보 카드 */}
            <div className={CARD}>
              <SectionHeader
                icon={<AccountInfoIcon />}
                title={t('settings.account_info')}
                badge={t('settings.account_common_badge')}
                badgeStyle={BADGE_ACCOUNT}
              />

              {/* 이메일 + 비밀번호 변경하기 */}
              <div className="flex items-center gap-3 bg-[var(--color-gray-bg)] rounded-[8px] px-4 py-3">
                <div className="flex-shrink-0 w-9 h-9 rounded-full bg-[#64748b] flex items-center justify-center text-white text-[11px] font-semibold tracking-[-0.25px]">
                  {initials}
                </div>
                <div className="flex flex-col gap-1">
                  <span className="text-[14px] font-medium text-[#475569] tracking-[-0.35px]">
                    {userEmail || '—'}
                  </span>
                  <button
                    onClick={() => { setPasswordChangeError(null); setPasswordModalOpen(true); }}
                    className="self-start flex items-center gap-1 text-[13px] font-medium text-[#006fff] hover:opacity-75 transition-opacity tracking-[-0.325px]"
                  >
                    <LockIcon />
                    {t('settings.change_password_link')}
                  </button>
                </div>
              </div>

              <div className="h-3" />

              {/* 계정 정보 필드 — 2열 그리드 */}
              <div className="grid grid-cols-2 gap-x-6 gap-y-4">
                <ReadOnlyField label={t('settings.manager_name')}   value={company?.managerName}    placeholder={t('settings.manager_name_placeholder')} />
                <ReadOnlyField label={t('settings.contact')}        value={company?.managerNumber}  placeholder={t('settings.contact_placeholder')} />
                <ReadOnlyField label={t('settings.company_name')}   value={company?.companyName}    placeholder={t('settings.company_name_placeholder')} />
                <ReadOnlyField label={t('settings.biz_reg_no')}     value={company?.businessNumber} placeholder={t('settings.biz_reg_no_placeholder')} />
                <ReadOnlyField label={t('settings.main_service')}   value={company?.mainService}    placeholder={t('settings.main_service_placeholder')} />
                <ReadOnlyField label={t('settings.biz_type')}       value={company?.businessType}   placeholder={t('settings.biz_type_placeholder')} />
                <ReadOnlyField label={t('settings.employee_count')} value={company?.employeeCount}  placeholder={t('settings.employee_count_placeholder')} />
              </div>

              <div className="flex justify-end">
                <EditLinkBtn label={t('settings.account_edit_btn')} onClick={() => setAccountModalOpen(true)} />
              </div>
            </div>

            {/* 언어 카드 */}
            <div className={CARD}>
              <SectionHeader
                icon={<GlobeSettingsIcon />}
                title={t('settings.language_title')}
                badge={t('settings.account_common_badge')}
                badgeStyle={BADGE_ACCOUNT}
              />
              <LangSelector />
            </div>

          </div>

          {/* ── 우측: 프로젝트 정보 + 라이브니스 + 개인정보 동의 (프로젝트별 설정) ── */}
          <div className="flex-1 min-w-0 flex flex-col gap-5">

            {/* 프로젝트 정보 카드 — 가로 레이아웃 + 인라인 수정 */}
            <div className={CARD}>
              <SectionHeader
                icon={<ProjectInfoIcon />}
                title={t('settings.project_info_title')}
                badge={t('settings.project_badge')}
                badgeStyle={BADGE_PROJECT}
              />
              <div className="flex items-center justify-between gap-6">
                <div className="flex items-center gap-16 min-w-0">
                  <ReadOnlyField label={t('settings.project_name')} value={selectedProject?.name} />
                  <ReadOnlyField label={t('settings.purpose')}      value={selectedProject?.description} />
                </div>
                <EditLinkBtn label={t('settings.project_edit_btn')} onClick={() => setProjectModalOpen(true)} />
              </div>
            </div>

            {/* 라이브니스 기능 사용 여부 카드 — 얼굴/손바닥 패널 */}
            <div className={CARD}>
              <SectionHeader
                icon={<ActivityIcon />}
                title={t('settings.liveness_label')}
                badge={t('settings.project_badge')}
                badgeStyle={BADGE_PROJECT}
              />
              <p className="text-[14px] text-[#64748b] tracking-[-0.35px] leading-5">
                {t('settings.liveness_desc')}
              </p>
              {/* 디자인 1740:4220 — 얼굴/손바닥 패널 좌우 나란히 (gap 28px), 각각 독립 저장 */}
              <div className="flex gap-7 items-start">
                <LivenessPanel
                  variant="face"
                  modules={[
                    { label: t('module.enrollment'),   checked: livenessRegister,    onChange: (v) => handleLivenessToggle(v, livenessVerify, livenessVerifyImage, livenessMatch) },
                    { label: t('module.verification'), checked: livenessVerify,      onChange: (v) => handleLivenessToggle(livenessRegister, v, livenessVerifyImage, livenessMatch) },
                    { label: t('module.verify_image'), checked: livenessVerifyImage, onChange: (v) => handleLivenessToggle(livenessRegister, livenessVerify, v, livenessMatch) },
                    { label: t('module.matching'),     checked: livenessMatch,       onChange: (v) => handleLivenessToggle(livenessRegister, livenessVerify, livenessVerifyImage, v) },
                  ]}
                />
                <LivenessPanel
                  variant="palm"
                  modules={[
                    { label: t('module.enrollment'), checked: palmLivenessRegister, onChange: (v) => handlePalmLivenessToggle(v, palmLivenessMatch) },
                    { label: t('module.matching'),   checked: palmLivenessMatch,    onChange: (v) => handlePalmLivenessToggle(palmLivenessRegister, v) },
                  ]}
                />
              </div>
            </div>

            {/* 개인정보 노출 동의 카드 */}
            <div className={CARD}>
              <SectionHeader
                icon={<ShieldIcon />}
                title={t('settings.consent_title')}
                badge={t('settings.project_badge')}
                badgeStyle={BADGE_PROJECT}
              />
              <p className="text-[13px] text-[#64748b] tracking-[-0.325px] leading-[1.5]">
                {t('settings.consent_desc')}
              </p>

              {/* 변경 이력 버튼 — 우측 정렬 */}
              <div className="flex justify-end">
                <button onClick={() => setHistoryOpen(true)} className="flex items-center gap-1 text-[14px] text-[#475569] tracking-[-0.35px] hover:opacity-75 transition-opacity">
                  {t('settings.consent_change_history')}
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" style={{ transform: 'rotate(90deg)' }}>
                    <polyline points="18 15 12 9 6 15" />
                  </svg>
                </button>
              </div>

              <div className="rounded-[12px] bg-[#f5f6f8] px-[20px] py-[12px] flex flex-col gap-[8px]">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <span className="text-[16px] font-normal text-[#334155] tracking-[-0.4px] leading-[1.5]">
                      {t('settings.consent_title')}
                    </span>
                    {consentEnabled ? (
                      <span className="inline-flex items-center gap-1 px-3 py-1 border border-[#f59e0b] rounded-[8px] bg-white text-[#f59e0b]">
                        <ShieldIcon />
                        <span className="text-[12px] font-semibold tracking-[-0.3px]">
                          {t('settings.consent_agreed_badge')}
                        </span>
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 px-3 py-1 border border-[#334155] rounded-[8px] bg-white text-[#334155]">
                        <ShieldIcon />
                        <span className="text-[12px] font-semibold tracking-[-0.3px]">
                          {t('settings.consent_not_agreed_badge')}
                        </span>
                      </span>
                    )}
                  </div>
                  <Toggle
                    checked={consentEnabled}
                    onChange={(v) => setConsentConfirmPending(v)}
                  />
                </div>
                {consentAgreedAt && (
                  <div className="flex items-center gap-1">
                    <InfoCircleIcon />
                    <span className={['text-[13px] tracking-[-0.325px]', consentEnabled ? 'text-[#334155]' : 'text-[#64748b]'].join(' ')}>
                      {t('settings.consent_last_changed', { date: formatConsentDate(consentAgreedAt) })}
                    </span>
                  </div>
                )}
              </div>

              <div className={['border border-dashed rounded-[8px] px-[16px] py-[8px]', consentEnabled ? 'border-[#fac5c5]' : 'border-[#0b8b61]'].join(' ')}>
                <p className={['text-[14px] font-semibold tracking-[-0.35px] leading-[1.4]', consentEnabled ? 'text-[#ef4444]' : 'text-[#0fb981]'].join(' ')}>
                  {consentEnabled ? t('settings.consent_note_active') : t('settings.consent_note_inactive')}
                </p>
              </div>
            </div>


          </div>
        </div>
      </div>

      {/* 동의 변경 이력 모달 */}
      {historyOpen && (
        <ConsentHistoryModalWrapper projectId={projectId} onClose={() => setHistoryOpen(false)} />
      )}

      {/* 개인정보 동의 확인 모달 */}
      {consentConfirmPending !== null && (
        <ConsentConfirmModal
          activating={consentConfirmPending}
          onConfirm={() => {
            setConsentEnabled(consentConfirmPending);
            if (projectId) saveConsent(consentConfirmPending);
            setConsentConfirmPending(null);
          }}
          onCancel={() => setConsentConfirmPending(null)}
        />
      )}

      {/* 라이브니스 업데이트 토스트 */}
      {livenessToast && (
        <Alert
          message={t('settings.liveness_updated')}
          variant="success"
          onClose={() => setLivenessToast(false)}
        />
      )}

      {/* 계정 정보 수정 모달 */}
      <AccountEditModal
        isOpen={accountModalOpen}
        onClose={() => setAccountModalOpen(false)}
        onSubmit={(data) => saveCompany(data)}
        loading={isSavingCompany}
        initialData={company ? {
          name:          company.managerName,
          contact:       company.managerNumber,
          companyName:   company.companyName,
          bizRegNo:      company.businessNumber,
          mainService:   company.mainService,
          bizType:       company.businessType,
          employeeCount: company.employeeCount,
        } : undefined}
      />

      {/* 프로젝트 정보 수정 모달 */}
      <ProjectEditModal
        isOpen={projectModalOpen}
        onClose={() => setProjectModalOpen(false)}
        onSubmit={(data) => { if (projectId) saveProject(data); }}
        loading={isSavingProject}
        initialData={{
          projectName: selectedProject?.name        ?? '',
          purpose:     selectedProject?.description ?? '',
        }}
      />

      {/* 계정 정보 수정 완료 팝업 */}
      {successData && (
        <AccountEditSuccessModal data={successData} onClose={() => setSuccessData(null)} />
      )}

      {/* 프로젝트 정보 수정 완료 팝업 */}
      {projectSuccessData && (
        <ProjectEditSuccessModal data={projectSuccessData} onClose={() => setProjectSuccessData(null)} />
      )}

      {/* 비밀번호 변경 모달 */}
      <PasswordChangeModal
        isOpen={passwordModalOpen}
        onClose={() => { setPasswordModalOpen(false); setPasswordChangeError(null); }}
        onSubmit={(password, newPassword) => {
          setPasswordChangeError(null);
          doChangePassword({ password, newPassword });
        }}
        loading={isChangingPassword}
        error={passwordChangeError}
      />

      {/* 비밀번호 변경 완료 팝업 */}
      {passwordChangeSuccess && (
        <PasswordChangeSuccessModal onClose={() => setPasswordChangeSuccess(false)} />
      )}
    </DashboardLayout>
  );
}

export default SettingsPage;

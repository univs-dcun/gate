/**
 * ProjectCreatedPanel — 프로젝트 생성 완료 결과 패널
 *
 * Figma node 1768:7924 기반.
 * 체크 + 완료 메시지(남은 개수) + 정보(이름/목적/API키 다크박스) + 데모 체험 바로가기 + 대시보드 이동
 */

import { useState } from 'react';
import { useTranslation } from 'react-i18next';

interface ProjectCreatedPanelProps {
  projectName: string;
  purpose:     string;
  apiKey:      string;
  demoUrl:     string;
  /** 남은 생성 가능 개수 — 있으면 완료 메시지 2번째 줄 표시 */
  remaining?:  number;
  onGoDashboard: () => void;
}

/* ── 아이콘 ── */
const CheckIcon = () => (
  <svg width="28" height="28" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <polyline points="20 6 9 17 4 12" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);
const CopyIcon = ({ color = 'currentColor' }: { color?: string }) => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
  </svg>
);
const LightningIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="var(--color-primary-700, #004399)" aria-hidden="true">
    <path d="M13 2L4.5 13.5H11l-1 8.5L19.5 10H13z" />
  </svg>
);
const ExternalLinkIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
    <polyline points="15 3 21 3 21 9" /><line x1="10" y1="14" x2="21" y2="3" />
  </svg>
);

function useCopy() {
  const [copied, setCopied] = useState(false);
  const copy = (text: string) => {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };
  return { copied, copy };
}

export default function ProjectCreatedPanel({ projectName, purpose, apiKey, demoUrl, remaining, onGoDashboard }: ProjectCreatedPanelProps) {
  const { t } = useTranslation();
  const apiKeyCopy = useCopy();
  const urlCopy = useCopy();

  return (
    <div className="fixed inset-0 z-[var(--z-modal)] flex" aria-modal="true" role="dialog">
      <div className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]" onClick={onGoDashboard} />

      <div
        className={[
          'absolute top-[26px] right-[18px]',
          'w-[744px] max-w-[calc(100vw-36px)] h-[calc(100vh-53px)]',
          'bg-white rounded-[16px] flex flex-col overflow-hidden shadow-[var(--shadow-xl)]',
        ].join(' ')}
        onClick={(e) => e.stopPropagation()}
      >
        {/* 콘텐츠 */}
        <div className="flex-1 overflow-y-auto px-9 py-10 flex flex-col items-center justify-center gap-10">

          {/* 체크 + 완료 메시지 */}
          <div className="flex flex-col items-center gap-4 text-center">
            <div className="w-[52px] h-[52px] rounded-full bg-[var(--color-link-blue)] flex items-center justify-center flex-shrink-0">
              <CheckIcon />
            </div>
            <h2 className="text-[24px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.6px] leading-[36px]">
              {t('projects.create_success_title')}
            </h2>
            <div className="flex flex-col text-[18px] font-semibold text-[var(--color-neutral-500)] tracking-[-0.45px] leading-[28px]">
              <span>{t('projects.create_success_subtitle')}</span>
              {remaining !== undefined && (
                <span>{t('projects.create_success_remaining', { count: remaining })}</span>
              )}
            </div>
          </div>

          {/* 정보 + 데모 섹션 */}
          <div className="w-full max-w-[576px] flex flex-col gap-8">

            {/* 정보 행 */}
            <div className="flex flex-col gap-4">
              <InfoRow label={t('projects.name_label')} value={projectName} />
              <div className="h-px bg-[var(--color-neutral-200)]" />
              <InfoRow label={t('settings.purpose')} value={purpose || '—'} />
              <div className="h-px bg-[var(--color-neutral-200)]" />

              {/* API 키 */}
              <div className="flex flex-col gap-4">
                <span className="text-[16px] font-normal text-[var(--color-neutral-500)] tracking-[-0.4px]">
                  {t('projects.api_key_label')}
                </span>
                <div className="flex items-center justify-between gap-3 h-[43px] px-6 py-3 bg-[var(--color-neutral-700)] rounded-[12px]">
                  <span className="font-mono text-[14px] font-semibold text-white tracking-[-0.35px] truncate">
                    {apiKey || '—'}
                  </span>
                  <button
                    type="button"
                    onClick={() => apiKeyCopy.copy(apiKey)}
                    className="flex items-center gap-1 flex-shrink-0 text-white hover:opacity-80 transition-opacity"
                  >
                    <CopyIcon color="white" />
                    <span className="text-[14px] font-semibold tracking-[-0.35px]">
                      {apiKeyCopy.copied ? t('projects.copied') : t('projects.copy')}
                    </span>
                  </button>
                </div>
              </div>
            </div>

            {/* 데모 체험 바로가기 */}
            <div className="flex flex-col gap-3 p-6 bg-[var(--color-neutral-50-bg)] rounded-[12px]">
              <div className="flex items-center gap-1">
                <LightningIcon />
                <span className="text-[16px] font-semibold text-[var(--color-primary-700,#004399)] tracking-[-0.4px]">
                  {t('projects.demo_shortcut')}
                </span>
              </div>
              <div className="flex flex-col gap-[9px] px-6 py-5 bg-white border border-[var(--color-neutral-300)] rounded-[12px]">
                <span className="text-[16px] font-semibold text-[var(--color-neutral-700)] tracking-[-0.4px]">
                  {t('projects.demo_url_label')}
                </span>
                <div className="flex items-center justify-between gap-4">
                  <div className="flex items-end gap-2 min-w-0">
                    <span className="text-[14px] font-semibold text-[var(--color-link-blue)] tracking-[-0.35px] leading-[20px] break-all">
                      {demoUrl}
                    </span>
                    <button
                      type="button"
                      onClick={() => urlCopy.copy(demoUrl)}
                      className="flex-shrink-0 text-[var(--color-neutral-400)] hover:text-[var(--color-neutral-600)] transition-colors"
                      title={urlCopy.copied ? t('projects.copied') : t('projects.copy')}
                    >
                      <CopyIcon />
                    </button>
                  </div>
                  <a
                    href={demoUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="flex items-center gap-2 flex-shrink-0 px-3 py-2 bg-[var(--color-link-blue)] hover:bg-[var(--color-link-blue-hover)] rounded-[8px] text-white text-[14px] font-semibold tracking-[-0.35px] transition-colors"
                  >
                    {t('projects.go')}
                    <ExternalLinkIcon />
                  </a>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* 하단: 대시보드로 이동 */}
        <div className="flex-shrink-0 px-9 pb-9">
          <button
            type="button"
            onClick={onGoDashboard}
            className="w-full h-12 rounded-[8px] bg-[var(--color-link-blue)] hover:bg-[var(--color-link-blue-hover)] text-white text-[18px] font-semibold tracking-[-0.45px] transition-colors"
          >
            {t('projects.go_dashboard')}
          </button>
        </div>
      </div>
    </div>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <span className="text-[16px] font-normal text-[var(--color-neutral-500)] tracking-[-0.4px] whitespace-nowrap">
        {label}
      </span>
      <span className="text-[16px] font-semibold text-[var(--color-neutral-700)] tracking-[-0.4px] text-right break-words">
        {value}
      </span>
    </div>
  );
}

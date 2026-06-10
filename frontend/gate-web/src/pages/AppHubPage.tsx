/**
 * AppHubPage — 외부 연동형 프로젝트 전용 앱 허브
 *
 * 유니버스 인증 데모 앱 정보 + QR + 관리자 링크 표시
 */

import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate } from 'react-router-dom';
import QRCode from 'qrcode';
import { useProjectContext } from '@/contexts/ProjectContext';
import { DashboardLayout } from '@/components/layout';

const BASE_URL   = 'https://develop.univs.ai:7778';
const ADMIN_URL  = `${BASE_URL}/admin`;
const mobileUrl  = (apiKey: string) => `${BASE_URL}/mobile?apikey=${apiKey}`;

/* ── 아이콘 ── */
const CopyIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="9" y="9" width="13" height="13" rx="2" /><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1" />
  </svg>
);
const ExternalLinkIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M18 13v6a2 2 0 01-2 2H5a2 2 0 01-2-2V8a2 2 0 012-2h6" /><polyline points="15 3 21 3 21 9" /><line x1="10" y1="14" x2="21" y2="3" />
  </svg>
);
const CheckIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="20 6 9 17 4 12" />
  </svg>
);
const PhoneIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <rect x="5" y="2" width="14" height="20" rx="2" ry="2" /><line x1="12" y1="18" x2="12.01" y2="18" />
  </svg>
);
const ShieldIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
  </svg>
);

/* ── 앱 일러스트 (인라인 SVG) ── */
const AppIllustration = () => (
  <svg width="100%" height="100%" viewBox="0 0 280 180" fill="none" xmlns="http://www.w3.org/2000/svg">
    {/* 배경 그라데이션 */}
    <defs>
      <linearGradient id="bg-grad" x1="0" y1="0" x2="280" y2="180" gradientUnits="userSpaceOnUse">
        <stop offset="0%" stopColor="#EFF9FF" />
        <stop offset="100%" stopColor="#F0EEFF" />
      </linearGradient>
      <linearGradient id="phone-grad" x1="100" y1="20" x2="180" y2="160" gradientUnits="userSpaceOnUse">
        <stop offset="0%" stopColor="#006FFF" />
        <stop offset="100%" stopColor="#8A58FF" />
      </linearGradient>
    </defs>
    <rect width="280" height="180" rx="12" fill="url(#bg-grad)" />
    {/* 폰 본체 */}
    <rect x="100" y="18" width="80" height="144" rx="12" fill="url(#phone-grad)" />
    <rect x="104" y="30" width="72" height="120" rx="8" fill="white" opacity="0.95" />
    {/* 홈 버튼 */}
    <circle cx="140" cy="155" r="5" fill="white" opacity="0.6" />
    {/* 화면 — 얼굴 아이콘 영역 */}
    <rect x="112" y="38" width="56" height="56" rx="6" fill="#EFF9FF" />
    {/* 얼굴 윤곽 */}
    <circle cx="140" cy="58" r="14" stroke="#006FFF" strokeWidth="2" fill="none" />
    <circle cx="135" cy="55" r="2" fill="#006FFF" />
    <circle cx="145" cy="55" r="2" fill="#006FFF" />
    <path d="M134 63 Q140 68 146 63" stroke="#006FFF" strokeWidth="1.8" fill="none" strokeLinecap="round" />
    {/* 인증 완료 체크 */}
    <circle cx="154" cy="40" r="8" fill="#22C55E" />
    <polyline points="150,40 153,43 158,37" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none" />
    {/* 하단 정보 바 */}
    <rect x="112" y="102" width="56" height="8" rx="4" fill="#E2E8F0" />
    <rect x="112" y="102" width="38" height="8" rx="4" fill="#006FFF" opacity="0.7" />
    <rect x="112" y="116" width="56" height="6" rx="3" fill="#F1F5F9" />
    <rect x="112" y="126" width="40" height="6" rx="3" fill="#F1F5F9" />
    {/* 좌측 장식 */}
    <circle cx="48" cy="60" r="28" fill="#006FFF" opacity="0.06" />
    <circle cx="48" cy="60" r="18" fill="#006FFF" opacity="0.08" />
    <path d="M38 60 Q48 48 58 60 Q48 72 38 60Z" fill="#006FFF" opacity="0.25" />
    {/* 우측 장식 */}
    <circle cx="232" cy="110" r="24" fill="#8A58FF" opacity="0.06" />
    <circle cx="232" cy="110" r="15" fill="#8A58FF" opacity="0.09" />
    {/* 연결선 */}
    <path d="M76 60 Q88 60 100 66" stroke="#006FFF" strokeWidth="1.5" strokeDasharray="4 3" opacity="0.4" />
    <path d="M180 90 Q206 100 216 110" stroke="#8A58FF" strokeWidth="1.5" strokeDasharray="4 3" opacity="0.4" />
  </svg>
);

/* ── 복사 버튼 ── */
function CopyButton({ text }: { text: string }) {
  const { t } = useTranslation();
  const [copied, setCopied] = useState(false);
  const handleCopy = async () => {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <button
      type="button"
      onClick={handleCopy}
      className={[
        'flex items-center gap-1.5 h-[32px] px-3 rounded-[6px] text-[13px] font-medium tracking-[-0.325px] transition-colors',
        copied
          ? 'bg-[var(--color-task-bg)] text-[var(--color-task)]'
          : 'border border-[#CBD5E1] text-[#475569] hover:bg-[#F8FAFC]',
      ].join(' ')}
    >
      {copied ? <CheckIcon /> : <CopyIcon />}
      {copied ? t('app_hub.copied') : t('app_hub.copy')}
    </button>
  );
}

/* ── QR 캔버스 ── */
function QRCanvas({ url }: { url: string }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    if (!canvasRef.current || !url) return;
    QRCode.toCanvas(canvasRef.current, url, { width: 160, margin: 1 });
  }, [url]);
  return <canvas ref={canvasRef} className="rounded-[8px]" />;
}

/* ── 링크 행 ── */
function LinkRow({ label, desc, url, icon }: { label: string; desc: string; url: string; icon: React.ReactNode }) {
  const { t } = useTranslation();
  return (
    <div className="flex items-start justify-between gap-4 py-4">
      <div className="flex items-start gap-3 min-w-0">
        <span className="flex-shrink-0 w-9 h-9 rounded-[8px] bg-[var(--color-blue-10)] flex items-center justify-center text-[var(--color-link-blue)]">
          {icon}
        </span>
        <div className="flex flex-col gap-0.5 min-w-0">
          <span className="text-[14px] font-semibold text-[#1E293B] tracking-[-0.35px]">{label}</span>
          <span className="text-[13px] font-normal text-[#64748B] tracking-[-0.325px] leading-[1.5]">{desc}</span>
          <span className="text-[12px] font-mono text-[#94A3B8] tracking-[-0.3px] truncate max-w-[360px]">{url}</span>
        </div>
      </div>
      <div className="flex items-center gap-2 flex-shrink-0">
        <CopyButton text={url} />
        <a
          href={url}
          target="_blank"
          rel="noopener noreferrer"
          className={[
            'flex items-center gap-1.5 h-[32px] px-3 rounded-[6px]',
            'bg-[var(--color-link-blue)] hover:bg-[var(--color-link-blue-hover)]',
            'text-[13px] font-medium text-white tracking-[-0.325px] transition-colors',
          ].join(' ')}
        >
          <ExternalLinkIcon />
          {t('app_hub.open')}
        </a>
      </div>
    </div>
  );
}

/* ── 메인 컴포넌트 ── */
export default function AppHubPage() {
  const { t } = useTranslation();
  const { selectedProject, isLoading } = useProjectContext();
  const apiKey = selectedProject?.apiKey ?? '';
  const mobUrl = mobileUrl(apiKey);

  // 로딩 완료 후 EXTERNAL이 아니면 대시보드로 이동
  if (!isLoading && selectedProject?.projectType !== 'EXTERNAL') {
    return <Navigate to="/dashboard" replace />;
  }

  return (
    <DashboardLayout>
    <div className="p-6 flex flex-col gap-5">
      {/* 페이지 타이틀 */}
      <h1 className="text-[26px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.65px] leading-[var(--leading-normal)]">
        {t('app_hub.title')}
      </h1>
      <p className="mt-0 text-[15px] text-[#64748B] tracking-[-0.375px]">
        {t('app_hub.subtitle')}
      </p>

      {/* 앱 카드 */}
      <div className="mt-0 bg-white rounded-[16px] border border-[var(--color-border-default)] shadow-[var(--card-shadow)] overflow-hidden">

        {/* 카드 헤더 — 일러스트 */}
        <div className="h-[180px] overflow-hidden bg-gradient-to-l from-[#f0f1ff] from-[46%] to-[0%] to-[#eff8ff]">
          <AppIllustration />
        </div>

        <div className="px-7 py-6 flex flex-col gap-6">

          {/* 앱 이름 + 뱃지 */}
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-[10px] bg-gradient-to-br from-[#edf3ff] to-[#8A58FF] flex items-center justify-center flex-shrink-0">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
              </svg>
            </div>
            <div className="flex flex-col gap-0.5">
              <div className="flex items-center gap-2">
                <span className="text-[18px] font-bold text-[#1E293B] tracking-[-0.45px]">
                  {t('app_hub.univs_demo_app')}
                </span>
                <span className="inline-flex items-center px-2 py-0.5 rounded-full bg-[var(--color-blue-10)] text-[var(--color-link-blue)] text-[11px] font-semibold tracking-[-0.275px]">
                  {t('app_hub.univs_demo_badge')}
                </span>
              </div>
              <span className="text-[13px] text-[#64748B] tracking-[-0.325px]">
                {t('app_hub.univs_demo_desc')}
              </span>
            </div>
          </div>

          {/* QR + 모바일 링크 */}
          <div className="flex gap-6 p-5 bg-[#F8FAFC] rounded-[12px] border border-[#E2E8F0]">
            {/* QR */}
            <div className="flex-shrink-0 flex flex-col items-center gap-2">
              {apiKey ? (
                <QRCanvas url={mobUrl} />
              ) : (
                <div className="w-[160px] h-[160px] rounded-[8px] bg-[#E2E8F0] flex items-center justify-center">
                  <span className="text-[12px] text-[#94A3B8]">{t('support.no_api_key')}</span>
                </div>
              )}
              <span className="text-[11px] text-[#94A3B8] tracking-[-0.275px]">
                {t('app_hub.mobile_desc')}
              </span>
            </div>

            {/* 링크 목록 */}
            <div className="flex-1 flex flex-col divide-y divide-[#E2E8F0]">
              <LinkRow
                label={t('app_hub.mobile_label')}
                desc={t('app_hub.mobile_desc')}
                url={mobUrl}
                icon={<PhoneIcon />}
              />
              <LinkRow
                label={t('app_hub.admin_label')}
                desc={t('app_hub.admin_desc')}
                url={ADMIN_URL}
                icon={<ShieldIcon />}
              />
            </div>
          </div>

        </div>
      </div>
    </div>
    </DashboardLayout>
  );
}

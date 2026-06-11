import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { DashboardLayout } from '@/components/layout';
import { Alert } from '@/components/ui';
import { useProjectContext } from '@/contexts/ProjectContext';
import { getWebhookConfig, saveWebhookConfig, deleteWebhookConfig } from '@/services/webhook';

/* ── SVG 일러스트 (90×90) ── */
const ApiKeyIllust = () => (
  <svg width="90" height="90" viewBox="0 0 90 90" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect x="8" y="14" width="74" height="62" rx="6" fill="#EFF6FF" stroke="#BFDBFE" strokeWidth="1.5"/>
    <rect x="18" y="26" width="32" height="6" rx="3" fill="#93C5FD"/>
    <rect x="18" y="38" width="24" height="6" rx="3" fill="#BFDBFE"/>
    <rect x="18" y="50" width="28" height="6" rx="3" fill="#BFDBFE"/>
    <circle cx="66" cy="44" r="12" fill="#2563EB" opacity="0.15"/>
    <path d="M62 44a4 4 0 1 1 8 0 4 4 0 0 1-8 0zm4-7a7 7 0 1 0 3.74 12.95l2.65 2.65 1.41-1.41-2.65-2.65A7 7 0 0 0 66 37z" fill="#2563EB"/>
  </svg>
);

const GuideIllust = () => (
  <svg width="90" height="90" viewBox="0 0 90 90" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect x="18" y="8" width="54" height="74" rx="5" fill="#F0FDF4" stroke="#BBF7D0" strokeWidth="1.5"/>
    <rect x="28" y="20" width="38" height="5" rx="2.5" fill="#86EFAC"/>
    <rect x="28" y="31" width="28" height="4" rx="2" fill="#BBF7D0"/>
    <rect x="28" y="41" width="32" height="4" rx="2" fill="#BBF7D0"/>
    <rect x="28" y="51" width="20" height="4" rx="2" fill="#BBF7D0"/>
    <circle cx="68" cy="66" r="14" fill="#16A34A" opacity="0.12"/>
    <path d="M68 56l2.47 7.6H78l-6.24 4.53 2.38 7.33L68 71.07l-6.14 4.39 2.38-7.33L58 63.6h7.53L68 56z" fill="#16A34A"/>
  </svg>
);

const EmailIllust = () => (
  <svg width="90" height="90" viewBox="0 0 90 90" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect x="8" y="20" width="74" height="52" rx="6" fill="#FFF7ED" stroke="#FED7AA" strokeWidth="1.5"/>
    <path d="M8 26l37 28 37-28" stroke="#FB923C" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
    <circle cx="68" cy="30" r="12" fill="#F97316" opacity="0.15"/>
    <path d="M64 30h8M68 26v8" stroke="#F97316" strokeWidth="2" strokeLinecap="round"/>
  </svg>
);

/* ── 아이콘 ── */
const CopyIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <rect x="9" y="9" width="13" height="13" rx="2" />
    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
  </svg>
);

const ExternalLinkIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>
    <polyline points="15 3 21 3 21 9"/>
    <line x1="10" y1="14" x2="21" y2="3"/>
  </svg>
);



const LinkIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#64748b" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>
    <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
  </svg>
);

const ExperimentIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#64748b" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M9 3h6M9 3v7L5 17a2 2 0 0 0 1.764 2.942A2 2 0 0 0 9 20h6a2 2 0 0 0 2.236-.942 2 2 0 0 0-.472-2.186L14 10V3"/>
  </svg>
);


/* ── API 키 마스킹 ── */
function maskApiKey(key: string): string {
  if (!key) return '—';
  const visible = key.slice(0, 9);
  return visible + '****************************';
}

/* ── 토글 스위치 ── */
function ToggleSwitch({ checked, onChange }: { checked: boolean; onChange: () => void }) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      onClick={onChange}
      className={[
        'relative inline-flex h-[30px] w-[50px] shrink-0 cursor-pointer rounded-full transition-colors duration-200',
        checked ? 'bg-[#006fff]' : 'bg-[#cbd5e1]',
      ].join(' ')}
    >
      <span
        className={[
          'absolute top-[3px] size-[24px] rounded-full bg-white shadow-sm transition-transform duration-200',
          checked ? 'translate-x-[23px]' : 'translate-x-[3px]',
        ].join(' ')}
      />
    </button>
  );
}

/* ── 상태 배지 ── */
function StatusBadge({ active }: { active: boolean }) {
  const { t } = useTranslation();
  return active ? (
    <span className="bg-[#eff9ff] px-[8px] py-[4px] rounded-[8px] text-[#006fff] text-[14px] font-medium tracking-[-0.35px] leading-[1.4]">
      {t('support.status_on')}
    </span>
  ) : (
    <span className="bg-[#f5f6f8] px-[8px] py-[4px] rounded-[8px] text-[#64748b] text-[14px] font-medium tracking-[-0.35px] leading-[1.4]">
      {t('support.status_off')}
    </span>
  );
}

/* ── 카드 공통 스타일 ── */
const CARD = [
  'bg-white border border-[#cbd5e1] rounded-[12px]',
  'px-[28px] py-[20px] flex flex-col gap-[16px] min-w-0',
].join(' ');

const ACTION_BTN = [
  'w-full h-[48px] flex items-center justify-center',
  'bg-[#f1f5f9] rounded-[8px]',
  'text-[18px] font-semibold text-[#334155] tracking-[-0.45px]',
  'transition-colors hover:bg-[#e2e8f0] active:bg-[#cbd5e1] cursor-pointer',
].join(' ');

/* ── 웹훅 설정 행 ── */
function WebhookRow({
  icon,
  label,
  active,
  onToggle,
}: {
  icon: React.ReactNode;
  label: string;
  active: boolean;
  onToggle: () => void;
}) {
  return (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-[8px]">
        {icon}
        <span className="text-[16px] font-medium text-[#334155] tracking-[-0.4px] leading-[1.4]">{label}</span>
        <StatusBadge active={active} />
      </div>
      <ToggleSwitch checked={active} onChange={onToggle} />
    </div>
  );
}

/* ── 개발지원 페이지 ── */
function DevSupportPage() {
  const { t, i18n } = useTranslation();
  const { selectedProject } = useProjectContext();
  const queryClient = useQueryClient();
  const projectId = selectedProject?.id ? Number(selectedProject.id) : undefined;
  const [copied, setCopied] = useState(false);
  /* 웹훅 설정 — 백엔드 연동 (GET/PUT/DELETE) */
  const [webhookEnabled, setWebhookEnabled] = useState(false);  // 설정 존재 여부
  const [webhookUrl, setWebhookUrl] = useState('');
  const [demoEnabled, setDemoEnabled] = useState(false);
  const [apiEnabled, setApiEnabled] = useState(false);
  const [webhookToast, setWebhookToast] = useState<{ message: string; variant: 'success' | 'error' } | null>(null);

  /* 웹훅 설정 조회 */
  const { data: webhookData } = useQuery({
    queryKey: ['webhook', projectId],
    queryFn:  () => getWebhookConfig(projectId!).then(r => r.data.data),
    enabled:  !!projectId,
    staleTime: 0,
  });
  useEffect(() => {
    if (webhookData && webhookData.webhookUrl !== undefined) {
      setWebhookEnabled(true);
      setWebhookUrl(webhookData.webhookUrl ?? '');
      setDemoEnabled(!!webhookData.demoEnabled);
      setApiEnabled(!!webhookData.apiEnabled);
    } else {
      setWebhookEnabled(false);
    }
  }, [webhookData]);

  /* 저장(PUT) */
  const { mutate: saveWebhook, isPending: isSavingWebhook } = useMutation({
    mutationFn: () => saveWebhookConfig(projectId!, { webhookUrl, demoEnabled, apiEnabled }).then(r => r.data.data),
    onSuccess: () => {
      setWebhookEnabled(true);
      queryClient.invalidateQueries({ queryKey: ['webhook', projectId] });
      setWebhookToast({ message: t('support.webhook_saved'), variant: 'success' });
    },
    onError: () => setWebhookToast({ message: t('support.webhook_save_error'), variant: 'error' }),
  });

  /* 삭제(DELETE) */
  const { mutate: removeWebhook, isPending: isDeletingWebhook } = useMutation({
    mutationFn: () => deleteWebhookConfig(projectId!),
    onSuccess: () => {
      setWebhookEnabled(false);
      setWebhookUrl(''); setDemoEnabled(false); setApiEnabled(false);
      queryClient.invalidateQueries({ queryKey: ['webhook', projectId] });
      setWebhookToast({ message: t('support.webhook_deleted'), variant: 'success' });
    },
    onError: () => setWebhookToast({ message: t('support.webhook_delete_error'), variant: 'error' }),
  });


  const apiKey = selectedProject?.apiKey ?? '';
  const guideBaseUrl = window.__APP_CONFIG__?.apiGuideUrl || (import.meta.env.VITE_API_GUIDE_URL as string | undefined);
  const guideUrl = guideBaseUrl
    ? `${guideBaseUrl.replace(/\/$/, '')}/lang=${i18n.language}`
    : undefined;

  const handleCopy = async () => {
    if (!apiKey) return;
    try {
      await navigator.clipboard.writeText(apiKey);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      /* clipboard 접근 불가 */
    }
  };

  const handleSendEmail = () => {
    window.location.href = `mailto:${t('support.email_address')}`;
  };

  return (
    <DashboardLayout>
      <div className="flex flex-col gap-5">

        {/* 페이지 제목 */}
        <h1 className="text-[26px] font-semibold text-[#1e293b] tracking-[-0.65px] leading-[1.4]">
          {t('support.title')}
        </h1>

        {/* 카드 3개 */}
        <div className="grid gap-4" style={{ gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>

          {/* ── 카드 1: API 키 설정 ── */}
          <div className={CARD}>
            <div className="flex flex-col gap-[14px]">
              <div className="flex items-start justify-between">
                <div className="flex flex-col gap-[8px]">
                  <p className="text-[13px] text-[#1e293b] tracking-[-0.325px] leading-[1.4]">{t('support.api_key_category')}</p>
                  <p className="text-[20px] font-semibold text-[#1e293b] tracking-[-0.5px] leading-[1.4]">{t('support.api_key_title')}</p>
                </div>
                <div className="shrink-0"><ApiKeyIllust /></div>
              </div>
              <p
                className="font-medium text-[#94a3b8] tracking-[-0.4px] leading-[1.4] break-all font-mono min-w-0 text-[16px]"
                title={apiKey}
              >
                {maskApiKey(apiKey)}
              </p>
            </div>
            <button
              className={[ACTION_BTN, copied ? 'bg-[#e2e8f0]' : ''].join(' ')}
              onClick={handleCopy}
            >
              <span className="flex items-center gap-2">
                <CopyIcon />
                {copied ? t('support.api_key_copied') : t('support.api_key_copy')}
              </span>
            </button>
          </div>

          {/* ── 카드 2: API 개발 가이드 ── */}
          <div className={CARD}>
            <div className="flex flex-col gap-[14px]">
              <div className="flex items-start justify-between">
                <div className="flex flex-col gap-[8px]">
                  <p className="text-[13px] text-[#1e293b] tracking-[-0.325px] leading-[1.4]">{t('support.guide_category')}</p>
                  <p className="text-[20px] font-semibold text-[#1e293b] tracking-[-0.5px] leading-[1.4]">{t('support.guide_title')}</p>
                </div>
                <div className="shrink-0"><GuideIllust /></div>
              </div>
              <p className="text-[16px] font-medium text-[#94a3b8] tracking-[-0.4px] leading-[1.4]">
                {t('support.guide_subtitle')}
              </p>
            </div>
            {guideUrl ? (
              <a href={guideUrl} target="_blank" rel="noopener noreferrer" className={ACTION_BTN}>
                <span className="flex items-center gap-2">
                  <ExternalLinkIcon />
                  {t('support.guide_open')}
                </span>
              </a>
            ) : (
              <button className={ACTION_BTN} disabled>{t('support.guide_open')}</button>
            )}
          </div>

          {/* ── 카드 3: 이메일 문의 ── */}
          <div className={CARD}>
            <div className="flex flex-col gap-[14px]">
              <div className="flex items-start justify-between">
                <div className="flex flex-col gap-[8px]">
                  <p className="text-[13px] text-[#1e293b] tracking-[-0.325px] leading-[1.4]">{t('support.email_category')}</p>
                  <p className="text-[20px] font-semibold text-[#1e293b] tracking-[-0.5px] leading-[1.4]">{t('support.email_title')}</p>
                </div>
                <div className="shrink-0"><EmailIllust /></div>
              </div>
              <p className="text-[16px] font-medium text-[#94a3b8] tracking-[-0.4px] leading-[1.4]">
                {t('support.email_address')}
              </p>
            </div>
            <button className={ACTION_BTN} onClick={handleSendEmail}>
              {t('support.email_send')}
            </button>
          </div>

        </div>

        {/* ── 웹훅 섹션 ── */}
        <div className="bg-white border border-[#cbd5e1] rounded-[12px] pl-[24px] pr-[56px] pt-[16px] pb-[32px]">

          {/* 섹션 타이틀 */}
          <p className="text-[18px] font-semibold text-[#334155] tracking-[-0.45px] leading-[1.4] pt-[8px] mb-[28px]">
            {t('support.webhook_tab_settings')}
          </p>

          {/* 웹훅 설정 */}
          <div className="flex gap-[50px]">

              {/* 왼쪽 패널 */}
              <div className="flex-1 flex flex-col gap-[24px] min-w-0">

                {/* 웹 훅 */}
                <div className="flex flex-col gap-[16px]">
                  <div className="flex flex-col gap-[4px]">
                    <p className="text-[20px] font-semibold text-[#334155] tracking-[-0.5px] leading-[1.4]">{t('support.webhook_title')}</p>
                    <p className="text-[13px] text-[#64748b] tracking-[-0.325px] leading-[1.4]">{t('support.webhook_desc')}</p>
                  </div>
                  <div className="flex flex-col gap-[12px]">
                    <div className="flex flex-col gap-[4px]">
                      <label className="text-[14px] font-semibold text-[#64748b] tracking-[-0.35px] leading-[1.4]">
                        {t('support.webhook_url')}
                      </label>
                      <div className="bg-white border border-[#cbd5e1] rounded-[8px] h-[48px] flex items-center px-[12px]">
                        <input
                          type="text"
                          value={webhookUrl}
                          onChange={e => setWebhookUrl(e.target.value)}
                          placeholder="https://your-server.com/webhook"
                          className="flex-1 text-[14px] font-medium text-[#334155] tracking-[-0.35px] leading-[1.4] outline-none placeholder:text-[#94a3b8] bg-transparent"
                        />
                      </div>
                    </div>
                  </div>
                </div>

                <div className="bg-[#e2e8f0] h-px w-full" />

                {/* 전송 — 웹훅을 발생시킬 서비스(데모/API) */}
                <div className="flex flex-col gap-[16px]">
                  <div className="flex flex-col gap-[4px]">
                    <p className="text-[20px] font-semibold text-[#334155] tracking-[-0.5px] leading-[1.4]">{t('support.webhook_send_title')}</p>
                    <p className="text-[13px] text-[#64748b] tracking-[-0.325px] leading-[1.4]">{t('support.webhook_send_desc')}</p>
                  </div>
                  <div className="flex flex-col gap-[16px]">
                    <WebhookRow
                      icon={<ExperimentIcon />}
                      label={t('support.webhook_demo')}
                      active={demoEnabled}
                      onToggle={() => setDemoEnabled(v => !v)}
                    />
                    <WebhookRow
                      icon={<LinkIcon />}
                      label="API"
                      active={apiEnabled}
                      onToggle={() => setApiEnabled(v => !v)}
                    />
                  </div>
                </div>

                {/* 저장 / 삭제 */}
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    disabled={!projectId || isSavingWebhook}
                    onClick={() => saveWebhook()}
                    className="h-[44px] px-6 rounded-[8px] bg-[#006fff] text-white text-[14px] font-semibold tracking-[-0.35px] hover:bg-[#0056cc] transition-colors disabled:opacity-50"
                  >
                    {t('common.save')}
                  </button>
                  <button
                    type="button"
                    disabled={!projectId || !webhookEnabled || isDeletingWebhook}
                    onClick={() => removeWebhook()}
                    className="h-[44px] px-6 rounded-[8px] border border-[#cbd5e1] bg-white text-[#475569] text-[14px] font-semibold tracking-[-0.35px] hover:bg-[#f1f5f9] transition-colors disabled:opacity-50"
                  >
                    {t('common.delete')}
                  </button>
                </div>

              </div>

            </div>

        </div>
      </div>

      {webhookToast && (
        <Alert
          message={webhookToast.message}
          variant={webhookToast.variant}
          onClose={() => setWebhookToast(null)}
        />
      )}
    </DashboardLayout>
  );
}



export default DevSupportPage;

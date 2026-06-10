import { useState, useEffect, useRef } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import axios from 'axios';
import { MobileLayout } from '@/components/layout';
import {
  demoRegister, demoVerify, demoIdentify, demoLiveness, getSdkConfig, getSdkConfigByCode,
  sdkRegister, sdkVerify, sdkIdentify, sdkLiveness,
} from '@/services/demo';
import type {
  UserResponseDTO, VerifyResponseDTO, IdentifyResponseDTO, LivenessResponseDTO, SdkConfig,
} from '@/services/demo';

/* ─── 타입 ─────────────────────────────────────────────── */
type ModuleType = 'register' | 'verify' | 'match' | 'liveness';
type MobileStep = 'welcome' | 'input' | 'capture' | 'submitting' | 'result' | 'error';

type ResultData =
  | { type: 'register'; data: UserResponseDTO }
  | { type: 'verify';   data: VerifyResponseDTO }
  | { type: 'match';    data: IdentifyResponseDTO }
  | { type: 'liveness'; data: LivenessResponseDTO };

/* ─── 유틸 ─────────────────────────────────────────────── */
function copyText(text: string) {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(text).catch(() => fallbackCopy(text));
  } else {
    fallbackCopy(text);
  }
}
function fallbackCopy(text: string) {
  const ta = document.createElement('textarea');
  ta.value = text;
  ta.style.cssText = 'position:fixed;opacity:0;pointer-events:none';
  document.body.appendChild(ta);
  ta.select();
  document.execCommand('copy');
  document.body.removeChild(ta);
}
function randomUUID(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.random() * 16 | 0;
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
  });
}

/** 세션 prefix: 페이지 최초 로드 시 1회 고정 (장비/세션 식별용 앞 8자리) */
function generateSessionPrefix(): string {
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    const arr = new Uint8Array(4);
    crypto.getRandomValues(arr);
    return Array.from(arr).map((b) => b.toString(16).padStart(2, '0')).join('');
  }
  return Math.floor(Math.random() * 0xffffffff).toString(16).padStart(8, '0');
}

/** 요청 ID: {세션prefix8}-{요청별UUID 나머지} — UUID v4 형식 유지 */
function generateRequestId(sessionPrefix: string): string {
  return sessionPrefix + randomUUID().slice(8);
}

/* ─── 아이콘 ─────────────────────────────────────────────── */
const FaceIdIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M9 3H5a2 2 0 0 0-2 2v4" /><path d="M15 3h4a2 2 0 0 1 2 2v4" />
    <path d="M9 21H5a2 2 0 0 1-2-2v-4" /><path d="M15 21h4a2 2 0 0 0 2-2v-4" />
    <circle cx="12" cy="10" r="3" />
    <path d="M9 17c0-1.7 1.3-3 3-3s3 1.3 3 3" />
  </svg>
);
const CopyIcon = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <rect x="9" y="9" width="13" height="13" rx="2" />
    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
  </svg>
);
const CheckSmIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#22c55e" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <polyline points="20 6 9 17 4 12" />
  </svg>
);
const BanIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" aria-hidden="true">
    <circle cx="12" cy="12" r="10" />
    <line x1="4.93" y1="4.93" x2="19.07" y2="19.07" />
  </svg>
);

/* ─── 등록 일러스트 ───────────────────────────────────────── */
const RegisterIllustration = () => (
  <svg width="200" height="180" viewBox="0 0 200 180" fill="none" aria-hidden="true">
    <circle cx="100" cy="95" r="75" fill="#eff9ff" />
    <ellipse cx="100" cy="148" rx="38" ry="22" fill="#c3e4ff" />
    <circle cx="100" cy="80" r="30" fill="#dbeeff" />
    <rect x="76" y="56" width="48" height="48" rx="8" fill="none" stroke="#006fff" strokeWidth="1.5" strokeDasharray="6 4" opacity="0.5" />
    <path d="M76 65 L76 56 L85 56" stroke="#006fff" strokeWidth="2.5" strokeLinecap="round" />
    <path d="M115 56 L124 56 L124 65" stroke="#006fff" strokeWidth="2.5" strokeLinecap="round" />
    <path d="M76 95 L76 104 L85 104" stroke="#006fff" strokeWidth="2.5" strokeLinecap="round" />
    <path d="M124 95 L124 104 L115 104" stroke="#006fff" strokeWidth="2.5" strokeLinecap="round" />
    <circle cx="128" cy="108" r="17" fill="#006fff" />
    <polyline points="120,108 126,114 136,102" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
  </svg>
);

/* ─── 공통 버튼 ──────────────────────────────────────────── */
function PrimaryBtn({ onClick, disabled, children }: { onClick?: () => void; disabled?: boolean; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={[
        'w-full h-[52px] rounded-2xl text-[18px] font-semibold tracking-[-0.45px] transition-colors',
        disabled
          ? 'bg-[#e8eef2] text-[#a4adb2] cursor-not-allowed'
          : 'bg-[#006fff] text-white hover:bg-[#0059d4] active:bg-[#0047aa]',
      ].join(' ')}
    >
      {children}
    </button>
  );
}
function OutlineBtn({ onClick, children }: { onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      className="w-full h-[52px] bg-white border border-[#006fff] rounded-2xl text-[#006fff] text-[18px] font-semibold tracking-[-0.45px] transition-colors hover:bg-[#eff9ff] active:bg-[#dbeeff]"
    >
      {children}
    </button>
  );
}

/* ─── Welcome 화면 ───────────────────────────────────────── */
function WelcomeScreen({
  type, meta, projectName, isTokenMode, onStart,
}: {
  type: ModuleType;
  meta: { category: string; title: string; description: string };
  projectName: string;
  isTokenMode?: boolean;
  onStart: () => void;
}) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col flex-1">
      <div className="flex-1 flex flex-col items-center justify-center gap-5">
        <span className={[
          'px-3 py-1 rounded-full text-[13px] font-semibold tracking-[-0.3px]',
          isTokenMode
            ? 'bg-[#fff7ed] border border-[#fed7aa] text-[#f97316]'
            : 'bg-[#eff9ff] text-[#006fff]',
        ].join(' ')}>
          {isTokenMode ? t('mobile_test.badge_token') : t('mobile_test.badge_demo')}
        </span>
        <div className="flex flex-col items-center gap-2 text-center">
          <div className="flex items-center gap-1.5 text-[#64748b]">
            <FaceIdIcon />
            <span className="text-[14px] font-medium tracking-[-0.35px]">{meta.category}</span>
          </div>
          <h1 className="text-[24px] font-semibold text-[#1e293b] tracking-[-0.6px] leading-tight">
            {meta.title}
          </h1>
          <p className="text-[14px] text-[#64748b] tracking-[-0.35px] leading-relaxed max-w-[260px]">
            {meta.description}
          </p>
        </div>
        {type === 'register' && <RegisterIllustration />}
      </div>

      <div className="flex flex-col gap-3 pt-4">
        <div className="w-full border border-[#e2e8f0] rounded-xl px-4 py-3 flex items-center gap-2">
          <span className="text-[13px] text-[#94a3b8] tracking-[-0.3px] flex-shrink-0">{t('mobile_test.project_label')}</span>
          <span className="w-px h-3.5 bg-[#e2e8f0] flex-shrink-0" />
          <span className="text-[13px] font-medium text-[#475569] tracking-[-0.3px] truncate">{projectName}</span>
        </div>
        <PrimaryBtn onClick={onStart}>{t('mobile_test.start')}</PrimaryBtn>
      </div>
    </div>
  );
}

/* ─── 메모 입력 화면 ─────────────────────────────────────── */
function InputScreen({ onNext }: { onNext: (memo: string) => void }) {
  const { t } = useTranslation();
  const [memo, setMemo] = useState('');
  const MAX = 10;

  return (
    <div className="flex flex-col flex-1">
      <div className="flex-1 flex flex-col gap-6 pt-4">
        <div className="flex items-center gap-1.5 text-[#64748b]">
          <FaceIdIcon />
          <span className="text-[14px] font-medium tracking-[-0.35px]">{t('mobile_test.input_label')}</span>
        </div>
        <h2 className="text-[24px] font-semibold text-[#1e293b] tracking-[-0.6px] leading-tight whitespace-pre-line">
          {t('mobile_test.input_title')}
        </h2>
        <div className="flex flex-col gap-1.5">
          <input
            type="text"
            value={memo}
            onChange={(e) => setMemo(e.target.value.slice(0, MAX))}
            placeholder={t('mobile_test.input_placeholder')}
            className="w-full pb-2.5 border-0 border-b border-[#94a3b8] focus:border-[#006fff] bg-transparent text-[15px] text-[#1e293b] placeholder-[#94a3b8] tracking-[-0.35px] outline-none transition-colors"
          />
          <div className="flex justify-end">
            <span className="text-[12px] text-[#94a3b8] tracking-[-0.3px]">{memo.length}/{MAX}</span>
          </div>
        </div>
      </div>

      <div className="pt-4">
        <PrimaryBtn onClick={() => onNext(memo)}>{t('mobile_test.next')}</PrimaryBtn>
      </div>
    </div>
  );
}

/* ─── 모듈별 라이브니스 설정 키 매핑 ──────────────────────── */
const MODULE_LIVENESS_KEY: Partial<Record<ModuleType, keyof SdkConfig>> = {
  register: 'livenessRegisterEnabled',
  verify:   'livenessVerifyingByIdEnabled',
  match:    'livenessIdentifyingEnabled',
};

/* ─── 카메라 촬영 화면 (풀 높이) ─────────────────────────── */
function CaptureScreen({
  requestId, apiKey, token, moduleType, onCapture,
}: {
  requestId: string;
  apiKey: string;
  token: string;
  moduleType: ModuleType;
  onCapture: (blob: Blob) => void;
}) {
  const { t } = useTranslation();
  const videoRef = useRef<HTMLVideoElement>(null);
  const [ready, setReady] = useState(false);
  const [cameraError, setCameraError] = useState('');
  const streamRef = useRef<MediaStream | null>(null);
  const [livenessEnabled, setLivenessEnabled] = useState<boolean | null>(null);
  const [configError, setConfigError] = useState(false);

  useEffect(() => {
    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: 'user' }, audio: false })
      .then((stream) => {
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          videoRef.current.onloadedmetadata = () => setReady(true);
        }
      })
      .catch(() => setCameraError(t('mobile_test.camera_error')));
    return () => { streamRef.current?.getTracks().forEach((track) => track.stop()); };
  }, [t]);

  /* 카메라 진입마다 라이브니스 설정 조회 */
  useEffect(() => {
    const configKey = MODULE_LIVENESS_KEY[moduleType];
    if (!configKey) return;
    if (token) {
      getSdkConfigByCode(token)
        .then((res) => setLivenessEnabled((res.data.data[configKey] as boolean) ?? null))
        .catch(() => setConfigError(true));
    } else if (apiKey) {
      getSdkConfig(apiKey)
        .then((res) => setLivenessEnabled((res.data.data[configKey] as boolean) ?? null))
        .catch(() => setConfigError(true));
    }
  }, [apiKey, token, moduleType]);

  const handleCapture = () => {
    const video = videoRef.current;
    if (!video) return;

    /* 가이드 원 크기(240px)에 해당하는 비디오 소스 영역 계산 (object-cover 방식) */
    const guideSize = 240;
    const vW = video.videoWidth;
    const vH = video.videoHeight;
    const scale = Math.max(guideSize / vW, guideSize / vH);
    const srcW = guideSize / scale;
    const srcH = guideSize / scale;
    const srcX = (vW - srcW) / 2;
    const srcY = (vH - srcH) / 2;

    const canvas = document.createElement('canvas');
    canvas.width  = Math.round(srcW);
    canvas.height = Math.round(srcH);
    canvas.getContext('2d')!.drawImage(video, srcX, srcY, srcW, srcH, 0, 0, canvas.width, canvas.height);
    canvas.toBlob((blob) => { if (blob) onCapture(blob); }, 'image/jpeg', 0.9);
  };

  return (
    <div className="flex flex-col h-full">
      <div className="flex-1 bg-[#0f172a] flex flex-col items-center justify-center overflow-hidden min-h-0">
        {cameraError ? (
          <p className="text-white text-[14px] text-center whitespace-pre-line px-8 leading-relaxed">{cameraError}</p>
        ) : (
          <>
            {/* 라이브니스 적용 여부 배지 — 에러 시 숨김 */}
            {moduleType !== 'liveness' && !configError && (
              <div className="mb-4">
                {livenessEnabled === null ? (
                  <span className="flex items-center gap-1.5 px-3 py-1.5 rounded-full border border-white/20 bg-white/10">
                    <span className="w-2.5 h-2.5 border-2 border-white/30 border-t-white rounded-full animate-spin flex-shrink-0" />
                    <span className="w-20 h-3 bg-white/20 rounded animate-pulse" />
                  </span>
                ) : (
                  <span className={[
                    'flex items-center gap-1.5 px-3 py-1.5 rounded-full border text-[13px] font-semibold tracking-[-0.3px]',
                    livenessEnabled
                      ? 'bg-red-500/20 border-red-400/60 text-red-300'
                      : 'bg-white/10 border-white/20 text-white',
                  ].join(' ')}>
                    {livenessEnabled
                      ? <span className="w-2 h-2 rounded-full bg-red-400 flex-shrink-0" />
                      : <BanIcon />
                    }
                    {t(livenessEnabled ? 'module_test.liveness_on' : 'module_test.liveness_off')}
                  </span>
                )}
              </div>
            )}

            {/* 카메라 원 */}
            <div className="relative w-[240px] h-[240px]">
              <div className="absolute inset-0 rounded-full overflow-hidden">
                <video ref={videoRef} autoPlay playsInline muted className="w-full h-full object-cover scale-x-[-1]" />
              </div>
              {!ready && (
                <div className="absolute inset-0 rounded-full bg-[#1e293b] flex items-center justify-center">
                  <div className="w-8 h-8 border-4 border-[#334155] border-t-[#006fff] rounded-full animate-spin" />
                </div>
              )}
              <svg
                className="absolute pointer-events-none"
                style={{ inset: '-20px', width: '280px', height: '280px' }}
                viewBox="0 0 280 280"
                fill="none"
              >
                <circle cx="140" cy="140" r="122" stroke="#006fff" strokeWidth="1.5" strokeDasharray="7 5" opacity="0.35" />
                <path d="M26 66 L26 26 L66 26" stroke="#006fff" strokeWidth="3" strokeLinecap="round" />
                <path d="M214 26 L254 26 L254 66" stroke="#006fff" strokeWidth="3" strokeLinecap="round" />
                <path d="M26 214 L26 254 L66 254" stroke="#006fff" strokeWidth="3" strokeLinecap="round" />
                <path d="M254 214 L254 254 L214 254" stroke="#006fff" strokeWidth="3" strokeLinecap="round" />
              </svg>
            </div>

            {/* 설명 메시지 — 에러 시 기본 메시지, 정상 시 liveness 상태별 문구 */}
            <p className="mt-7 text-white/65 text-[14px] tracking-[-0.35px] font-medium text-center px-6 whitespace-pre-line leading-relaxed">
              {!configError && moduleType !== 'liveness' && livenessEnabled !== null
                ? t(livenessEnabled ? 'mobile_test.liveness_on_desc' : 'mobile_test.liveness_off_desc')
                : t('mobile_test.look_front')
              }
            </p>
          </>
        )}
      </div>

      <div
        className="bg-[#f5f6f8] px-6 pt-5 flex flex-col gap-4 flex-shrink-0"
        style={{ paddingBottom: 'calc(40px + env(safe-area-inset-bottom))' }}
      >
        <p className="text-center text-[12px] text-[#94a3b8] tracking-[-0.3px]">{t('mobile_test.request_id_label', { id: requestId })}</p>
        <PrimaryBtn onClick={handleCapture} disabled={!ready || !!cameraError}>{t('mobile_test.capture')}</PrimaryBtn>
      </div>
    </div>
  );
}

/* ─── 처리 중 ─────────────────────────────────────────────── */
function SubmittingScreen() {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col flex-1 items-center justify-center gap-6">
      <div className="w-20 h-20 rounded-full bg-[#eff9ff] flex items-center justify-center">
        <div className="w-10 h-10 border-4 border-[#c3e4ff] border-t-[#006fff] rounded-full animate-spin" />
      </div>
      <p className="text-[16px] font-medium text-[#475569] tracking-[-0.4px]">{t('mobile_test.processing')}</p>
    </div>
  );
}

/* ─── 결과 화면 — register ───────────────────────────────── */
function RegisterResultScreen({
  data, capturedImageUrl, requestId, onRetry,
}: {
  data: UserResponseDTO;
  capturedImageUrl: string | null;
  requestId: string;
  onRetry: () => void;
}) {
  const { t } = useTranslation();
  const [copiedFid, setCopiedFid] = useState(false);
  const [copiedReqId, setCopiedReqId] = useState(false);

  const handleCopyFid = () => { copyText(data.faceId); setCopiedFid(true); setTimeout(() => setCopiedFid(false), 2000); };
  const handleCopyReqId = () => { copyText(requestId); setCopiedReqId(true); setTimeout(() => setCopiedReqId(false), 2000); };

  return (
    <div className="flex flex-col flex-1 min-h-0">
      <div className="flex-1 flex flex-col gap-5 pt-4 overflow-y-auto min-h-0">
        <div className="flex items-center gap-1.5 text-[#64748b]">
          <FaceIdIcon />
          <span className="text-[14px] font-medium tracking-[-0.35px]">{t('mobile_test.result_register_label')}</span>
        </div>

        <div className="flex flex-col items-center gap-4">
          <div className="relative w-[96px] h-[96px]">
            {capturedImageUrl ? (
              <img src={capturedImageUrl} alt={t('mobile_test.result_register_title')} className="w-full h-full rounded-full object-cover border-2 border-[#e2e8f0]" />
            ) : (
              <div className="w-full h-full rounded-full bg-[#e2e8f0] flex items-center justify-center">
                <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.5" aria-hidden="true">
                  <circle cx="12" cy="8" r="4" />
                  <path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" />
                </svg>
              </div>
            )}
            <div className="absolute -bottom-1 -right-1 w-8 h-8 rounded-full bg-[#22c55e] border-2 border-white flex items-center justify-center">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <polyline points="20 6 9 17 4 12" />
              </svg>
            </div>
          </div>
          <div className="flex flex-col items-center gap-1 text-center">
            <h2 className="text-[24px] font-semibold text-[#1e293b] tracking-[-0.6px]">{t('mobile_test.result_register_title')}</h2>
            <p className="text-[14px] text-[#64748b] tracking-[-0.35px]">{t('mobile_test.result_register_subtitle')}</p>
          </div>
        </div>

        <div className="w-full bg-white rounded-2xl border border-[#e2e8f0] overflow-hidden">
          <div className="px-4 py-4 flex items-center justify-between gap-3">
            <div className="flex flex-col gap-0.5 min-w-0">
              <span className="text-[12px] text-[#94a3b8] font-medium tracking-[-0.3px]">{t('mobile_test.fid_label')}</span>
              <span className="text-[13px] font-semibold text-[#1e293b] font-mono break-all leading-snug">{data.faceId}</span>
            </div>
            <button onClick={handleCopyFid} className="flex-shrink-0 w-8 h-8 flex items-center justify-center rounded-lg bg-[#f5f6f8] text-[#64748b] hover:bg-[#e2e8f0] transition-colors">
              {copiedFid ? <CheckSmIcon /> : <CopyIcon />}
            </button>
          </div>
          <div className="h-px bg-[#f0f4f8] mx-4" />
          <div className="px-4 py-4 flex items-center justify-between gap-3">
            <div className="flex flex-col gap-0.5 min-w-0">
              <span className="text-[12px] text-[#94a3b8] font-medium tracking-[-0.3px]">{t('mobile_test.request_id')}</span>
              <span className="text-[13px] font-medium text-[#475569] font-mono break-all leading-snug">{requestId}</span>
            </div>
            <button onClick={handleCopyReqId} className="flex-shrink-0 w-8 h-8 flex items-center justify-center rounded-lg bg-[#f5f6f8] text-[#64748b] hover:bg-[#e2e8f0] transition-colors">
              {copiedReqId ? <CheckSmIcon /> : <CopyIcon />}
            </button>
          </div>
        </div>
      </div>

      <div className="flex flex-col gap-3 pt-4">
        <PrimaryBtn onClick={onRetry}>{t('mobile_test.add_register')}</PrimaryBtn>
      </div>
    </div>
  );
}

/* ─── 결과 화면 — verify ────────────────────────────────── */
function VerifyResultScreen({
  result, capturedImageUrl, requestId, fid, onRetry,
}: {
  result: Extract<ResultData, { type: 'verify' }>;
  capturedImageUrl: string | null;
  requestId: string;
  fid: string;
  onRetry: () => void;
}) {
  const { t } = useTranslation();
  const { success, similarity, failureReason } = result.data;
  const pct = Number(similarity).toFixed(1);

  const [copiedFid, setCopiedFid]     = useState(false);
  const [copiedReqId, setCopiedReqId] = useState(false);

  const handleCopyFid   = () => { copyText(fid);       setCopiedFid(true);   setTimeout(() => setCopiedFid(false),   2000); };
  const handleCopyReqId = () => { copyText(requestId); setCopiedReqId(true); setTimeout(() => setCopiedReqId(false), 2000); };

  /* ── 프로필 이미지 ── */
  const ringColor  = success ? '#22c55e' : '#F59E0B';
  const badgeBg    = success ? '#22c55e' : '#ef4444';

  return (
    <div className="flex flex-col flex-1 min-h-0">
      <div className="flex-1 flex flex-col gap-5 pt-2 overflow-y-auto min-h-0">

        {/* 레이블 */}
        <p className="text-center text-[14px] font-medium text-[#475569] tracking-[-0.35px]">
          {t('mobile_test.verify_result_label')}
        </p>

        {/* 프로필 이미지 */}
        <div className="flex justify-center">
          <div className="relative">
            <div
              className="w-[100px] h-[100px] rounded-full overflow-hidden border-[3px]"
              style={{ borderColor: ringColor }}
            >
              {capturedImageUrl ? (
                <img src={capturedImageUrl} alt="" className="w-full h-full object-cover" />
              ) : (
                <div className="w-full h-full bg-[#e2e8f0] flex items-center justify-center">
                  <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.5" aria-hidden="true">
                    <circle cx="12" cy="8" r="4" /><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" />
                  </svg>
                </div>
              )}
            </div>
            {/* 뱃지 */}
            <div
              className="absolute -top-1 -right-1 w-7 h-7 rounded-full flex items-center justify-center border-2 border-white"
              style={{ background: badgeBg }}
            >
              {success ? (
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              ) : (
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              )}
            </div>
          </div>
        </div>

        {/* 타이틀 + 설명 */}
        <div className="flex flex-col items-center gap-2 text-center">
          <h2 className="text-[24px] font-semibold text-[#1e293b] tracking-[-0.6px]">
            {success ? t('mobile_test.verify_success') : t('mobile_test.verify_fail')}
          </h2>
          <p className="text-[14px] font-medium text-[#334155] tracking-[-0.35px] leading-relaxed max-w-[200px]">
            {success
              ? t('mobile_test.verify_success_desc', { pct })
              : (failureReason ?? t('mobile_test.verify_fail_desc', { pct }))}
          </p>
        </div>

        {/* 정보 카드 (성공 시만) */}
        {success && (
          <div className="bg-white rounded-[16px] px-6 py-1 flex flex-col">
            {/* 메모 */}
            <div className="flex items-center justify-between py-4">
              <span className="text-[16px] font-medium text-[#94a3b8] tracking-[-0.4px]">{t('common.memo')}</span>
              <span className="text-[16px] font-semibold text-[#1e293b] tracking-[-0.4px]">{result.data.userDescription ?? '-'}</span>
            </div>
            <div className="h-px bg-[#e2e8f0]" />
            {/* 스코어 */}
            <div className="flex items-center justify-between py-4">
              <span className="text-[16px] font-medium text-[#94a3b8] tracking-[-0.4px]">{t('common.score')}</span>
              <span className="text-[16px] font-semibold text-[#1e293b] tracking-[-0.4px]">{pct}%</span>
            </div>
            <div className="h-px bg-[#e2e8f0]" />
            {/* FID */}
            <div className="flex items-center justify-between py-4 gap-2">
              <div className="flex flex-col gap-0.5 min-w-0 flex-1">
                <span className="text-[14px] font-medium text-[#94a3b8] tracking-[-0.4px]">{t('mobile_test.fid_label')}</span>
                <span className="text-[13px] font-semibold text-[#1e293b] font-mono break-all leading-snug">{fid}</span>
              </div>
              <button onClick={handleCopyFid} className="flex-shrink-0 w-7 h-7 flex items-center justify-center text-[#64748b]">
                {copiedFid ? <CheckSmIcon /> : <CopyIcon />}
              </button>
            </div>
            <div className="h-px bg-[#e2e8f0]" />
            {/* 요청 ID */}
            <div className="flex items-center justify-between py-4 gap-2">
              <div className="flex flex-col gap-0.5 min-w-0 flex-1">
                <span className="text-[14px] font-medium text-[#94a3b8] tracking-[-0.4px]">{t('mobile_test.request_id')}</span>
                <span className="text-[13px] font-semibold text-[#1e293b] font-mono break-all leading-snug">{requestId}</span>
              </div>
              <button onClick={handleCopyReqId} className="flex-shrink-0 w-7 h-7 flex items-center justify-center text-[#64748b]">
                {copiedReqId ? <CheckSmIcon /> : <CopyIcon />}
              </button>
            </div>
          </div>
        )}
      </div>

      {/* 버튼 */}
      <div className="flex gap-2 pt-4">
        <PrimaryBtn onClick={onRetry}>{t('mobile_test.retake')}</PrimaryBtn>
      </div>
    </div>
  );
}

/* ─── 결과 화면 — match ──────────────────────────────────── */
function MatchResultScreen({
  result, capturedImageUrl, requestId, onRetry,
}: {
  result: Extract<ResultData, { type: 'match' }>;
  capturedImageUrl: string | null;
  requestId: string;
  onRetry: () => void;
}) {
  const { t } = useTranslation();
  const { success, similarity, faceId, failureReason } = result.data;
  const pct = Number(similarity).toFixed(1);

  const [copiedFid, setCopiedFid]     = useState(false);
  const [copiedReqId, setCopiedReqId] = useState(false);

  const handleCopyFid   = () => { copyText(faceId);    setCopiedFid(true);   setTimeout(() => setCopiedFid(false),   2000); };
  const handleCopyReqId = () => { copyText(requestId); setCopiedReqId(true); setTimeout(() => setCopiedReqId(false), 2000); };

  const ringColor = success ? '#006fff' : '#F59E0B';
  const badgeBg   = success ? '#22c55e' : '#ef4444';

  return (
    <div className="flex flex-col flex-1 min-h-0">
      <div className="flex-1 flex flex-col gap-5 pt-2 overflow-y-auto min-h-0">

        {/* 레이블 */}
        <p className="text-center text-[14px] font-medium text-[#475569] tracking-[-0.35px]">
          {t('mobile_test.match_result_label')}
        </p>

        {/* 프로필 이미지 */}
        <div className="flex justify-center">
          <div className="relative">
            <div
              className="w-[100px] h-[100px] rounded-full overflow-hidden border-[3px]"
              style={{ borderColor: ringColor }}
            >
              {capturedImageUrl ? (
                <img src={capturedImageUrl} alt="" className="w-full h-full object-cover" />
              ) : (
                <div className="w-full h-full bg-[#e2e8f0] flex items-center justify-center">
                  <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.5" aria-hidden="true">
                    <circle cx="12" cy="8" r="4" /><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" />
                  </svg>
                </div>
              )}
            </div>
            {/* 성공/실패 뱃지 */}
            <div
              className="absolute -top-1 -right-1 w-7 h-7 rounded-full flex items-center justify-center border-2 border-white"
              style={{ background: badgeBg }}
            >
              {success ? (
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              ) : (
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              )}
            </div>
          </div>
        </div>

        {/* 타이틀 + 설명 */}
        <div className="flex flex-col items-center gap-2 text-center">
          <h2 className="text-[24px] font-semibold text-[#1e293b] tracking-[-0.6px]">
            {success ? t('mobile_test.match_success') : t('mobile_test.match_fail')}
          </h2>
          <p className="text-[14px] font-medium text-[#334155] tracking-[-0.35px] leading-relaxed max-w-[200px]">
            {success
              ? t('mobile_test.match_success_desc', { pct })
              : (failureReason ?? t('mobile_test.match_fail_desc', { pct }))}
          </p>
        </div>

        {/* 정보 카드 (성공 시) */}
        {success && (
          <div className="bg-white rounded-[16px] px-6 py-1 flex flex-col">
            {/* 메모 */}
            <div className="flex items-center justify-between py-4">
              <span className="text-[16px] font-medium text-[#94a3b8] tracking-[-0.4px]">{t('common.memo')}</span>
              <span className="text-[16px] font-semibold text-[#1e293b] tracking-[-0.4px]">{result.data.userDescription ?? '-'}</span>
            </div>
            <div className="h-px bg-[#e2e8f0]" />
            {/* 스코어 */}
            <div className="flex items-center justify-between py-4">
              <span className="text-[16px] font-medium text-[#94a3b8] tracking-[-0.4px]">{t('common.score')}</span>
              <span className="text-[16px] font-semibold text-[#1e293b] tracking-[-0.4px]">{pct}%</span>
            </div>
            <div className="h-px bg-[#e2e8f0]" />
            {/* FID */}
            <div className="flex items-center justify-between py-4 gap-2">
              <div className="flex flex-col gap-0.5 min-w-0 flex-1">
                <span className="text-[14px] font-medium text-[#94a3b8] tracking-[-0.4px]">{t('mobile_test.fid_label')}</span>
                <span className="text-[13px] font-semibold text-[#1e293b] font-mono break-all leading-snug">{faceId}</span>
              </div>
              <button onClick={handleCopyFid} className="flex-shrink-0 w-7 h-7 flex items-center justify-center text-[#64748b]">
                {copiedFid ? <CheckSmIcon /> : <CopyIcon />}
              </button>
            </div>
            <div className="h-px bg-[#e2e8f0]" />
            {/* 요청 ID */}
            <div className="flex items-center justify-between py-4 gap-2">
              <div className="flex flex-col gap-0.5 min-w-0 flex-1">
                <span className="text-[14px] font-medium text-[#94a3b8] tracking-[-0.4px]">{t('mobile_test.request_id')}</span>
                <span className="text-[13px] font-semibold text-[#1e293b] font-mono break-all leading-snug">{requestId}</span>
              </div>
              <button onClick={handleCopyReqId} className="flex-shrink-0 w-7 h-7 flex items-center justify-center text-[#64748b]">
                {copiedReqId ? <CheckSmIcon /> : <CopyIcon />}
              </button>
            </div>
          </div>
        )}
      </div>

      {/* 버튼 */}
      <div className="flex gap-2 pt-4">
        <PrimaryBtn onClick={onRetry}>{t('mobile_test.confirm')}</PrimaryBtn>
      </div>
    </div>
  );
}

/* ─── 결과 화면 — liveness ───────────────────────────────── */
function LivenessResultScreen({ result, onRetry }: {
  result: Extract<ResultData, { type: 'liveness' }>;
  onRetry: () => void;
}) {
  const { t } = useTranslation();
  const { success, failureReason } = result.data;

  return (
    <div className="flex flex-col flex-1">
      <div className="flex-1 flex flex-col items-center justify-center gap-6">
        <div className={['w-16 h-16 rounded-full flex items-center justify-center', success ? 'bg-[#dcfce7]' : 'bg-[#fee2e2]'].join(' ')}>
          {success ? (
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#22c55e" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <polyline points="20 6 9 17 4 12" />
            </svg>
          ) : (
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#ef4444" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          )}
        </div>
        <h2 className="text-[24px] font-semibold text-[#1e293b] tracking-[-0.6px]">
          {success ? t('mobile_test.liveness_success') : t('mobile_test.liveness_fail')}
        </h2>
        <p className="text-[14px] text-[#64748b] text-center tracking-[-0.35px] leading-relaxed">
          {success
            ? t('mobile_test.liveness_success_desc')
            : (failureReason ?? t('mobile_test.liveness_fail_desc'))}
        </p>
      </div>
      <div className="pt-4">
        <OutlineBtn onClick={onRetry}>{t('mobile_test.retry_test')}</OutlineBtn>
      </div>
    </div>
  );
}

/* ─── 모드 비활성화 화면 ──────────────────────────────────── */
function ModeDisabledScreen({ mode, projectName, apiKey, token, moduleMeta }: {
  mode: 'demo' | 'token';
  projectName: string;
  apiKey: string;
  token: string;
  moduleMeta: { category: string; title: string };
}) {
  const { t } = useTranslation();
  const isTokenMode = mode === 'token';
  const maskedApiKey = apiKey.length > 8 ? `••••••••${apiKey.slice(-8)}` : apiKey;
  const maskedToken = token.length > 8 ? `••••••••${token.slice(-8)}` : token;
  const titleKey = isTokenMode ? 'mobile_test.token_disabled_title' : 'mobile_test.demo_disabled_title';
  const descKey  = isTokenMode ? 'mobile_test.token_disabled_desc'  : 'mobile_test.demo_disabled_desc';
  const iconColor = isTokenMode ? '#8A58FF' : '#f59e0b';
  const iconBg    = isTokenMode ? '#f3eeff' : '#fff7ed';
  return (
    <div className="flex flex-col flex-1">
      <div className="flex-1 flex flex-col items-center justify-center gap-6 px-2">
        <div className="w-16 h-16 rounded-full flex items-center justify-center" style={{ backgroundColor: iconBg }}>
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke={iconColor} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
            <path d="M7 11V7a5 5 0 0 1 10 0v4" />
          </svg>
        </div>
        <div className="flex flex-col items-center gap-2 text-center">
          <h2 className="text-[22px] font-semibold text-[#1e293b] tracking-[-0.55px] leading-tight">
            {t(titleKey)}
          </h2>
          <p className="text-[14px] text-[#64748b] tracking-[-0.35px] leading-relaxed max-w-[280px] whitespace-pre-line">
            {t(descKey)}
          </p>
        </div>

        {/* 프로젝트 정보 카드 */}
        <div className="w-full border border-[#e2e8f0] rounded-2xl overflow-hidden">
          <div className="px-4 py-2.5 bg-[#f8fafc] border-b border-[#e2e8f0]">
            <span className="text-[11px] font-semibold text-[#94a3b8] tracking-[0.5px] uppercase">
              {t('mobile_test.project_label')}
            </span>
          </div>
          <div className="flex flex-col divide-y divide-[#f1f5f9]">
            <div className="flex items-center justify-between px-4 py-3 gap-3">
              <span className="text-[13px] text-[#94a3b8] tracking-[-0.3px] flex-shrink-0">{t('mobile_test.project_name_label')}</span>
              <span className="text-[13px] font-semibold text-[#1e293b] tracking-[-0.3px] text-right truncate">{projectName}</span>
            </div>
            <div className="flex items-center justify-between px-4 py-3 gap-3">
              <span className="text-[13px] text-[#94a3b8] tracking-[-0.3px] flex-shrink-0">{t('mobile_test.module_label')}</span>
              <span className="text-[13px] font-semibold text-[#1e293b] tracking-[-0.3px]">{moduleMeta.title}</span>
            </div>
            {isTokenMode ? (
              <div className="flex items-center justify-between px-4 py-3 gap-3">
                <span className="text-[13px] text-[#94a3b8] tracking-[-0.3px] flex-shrink-0">{t('mobile_test.token_label')}</span>
                <span className="text-[12px] font-mono text-[#475569] tracking-[-0.3px]">{maskedToken}</span>
              </div>
            ) : (
              <div className="flex items-center justify-between px-4 py-3 gap-3">
                <span className="text-[13px] text-[#94a3b8] tracking-[-0.3px] flex-shrink-0">{t('mobile_test.api_key_label')}</span>
                <span className="text-[12px] font-mono text-[#475569] tracking-[-0.3px]">{maskedApiKey}</span>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

/* ─── 에러 화면 ──────────────────────────────────────────── */
function ErrorScreen({ message, onRetry }: { message: string; onRetry: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col flex-1">
      <div className="flex-1 flex flex-col items-center justify-center gap-6">
        <div className="w-16 h-16 rounded-full bg-[#fee2e2] flex items-center justify-center">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#ef4444" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
        </div>
        <p className="text-[16px] text-[#ef4444] text-center font-medium tracking-[-0.4px]">{message}</p>
      </div>
      <div className="pt-4">
        <OutlineBtn onClick={onRetry}>{t('mobile_test.retry')}</OutlineBtn>
      </div>
    </div>
  );
}

/* ─── 메인 페이지 ────────────────────────────────────────── */
function MobileTestPage() {
  const { t } = useTranslation();
  const { type } = useParams<{ type: string }>();
  const [searchParams] = useSearchParams();

  const MODULE_META = {
    register: { category: t('mobile_test.module_register_category'), title: t('mobile_test.module_register_title'), description: t('mobile_test.module_register_desc') },
    verify:   { category: t('mobile_test.module_verify_category'),   title: t('mobile_test.module_verify_title'),   description: t('mobile_test.module_verify_desc') },
    match:    { category: t('mobile_test.module_match_category'),    title: t('mobile_test.module_match_title'),    description: t('mobile_test.module_match_desc') },
    liveness: { category: t('mobile_test.module_liveness_category'), title: t('mobile_test.module_liveness_title'), description: t('mobile_test.module_liveness_desc') },
  };

  const moduleType = (type as ModuleType) ?? 'register';
  const meta = MODULE_META[moduleType] ?? MODULE_META.register;
  const apiKey   = searchParams.get('apikey')  ?? '';
  const token    = searchParams.get('token')   ?? '';
  const fid      = searchParams.get('fid')     ?? '';
  const testMode = token ? 'sdk' : 'demo';

  const [sessionPrefix] = useState(() => generateSessionPrefix());
  const [projectName, setProjectName] = useState<string>(
    searchParams.get('project') ?? t('mobile_test.default_project')
  );
  /* null = 확인 중, true = 활성, false = 비활성 */
  const [modeEnabled, setModeEnabled] = useState<boolean | null>(null);

  /** 설정 재조회 후 활성 여부 반환 (false 시 modeEnabled 상태도 갱신) */
  const checkMode = async (): Promise<boolean> => {
    try {
      if (token) {
        const res = await getSdkConfigByCode(token);
        const cfg = res.data.data;
        if (cfg?.projectName) setProjectName(cfg.projectName);
        const enabled = cfg?.sdkEnabled !== false;
        setModeEnabled(enabled);
        return enabled;
      }
      if (!apiKey) return true;
      const res = await getSdkConfig(apiKey);
      const cfg = res.data.data;
      if (cfg?.projectName) setProjectName(cfg.projectName);
      const enabled = cfg?.demoEnabled !== false;
      setModeEnabled(enabled);
      return enabled;
    } catch {
      return true; // 오류 시 차단하지 않음
    }
  };

  useEffect(() => {
    if (!apiKey && !token) return;
    checkMode();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [apiKey, token]);
  const [requestId, setRequestId] = useState(() => generateRequestId(sessionPrefix));
  const [step, setStep] = useState<MobileStep>('welcome');
  const [memo, setMemo] = useState('');
  const [result, setResult] = useState<ResultData | null>(null);
  const [errorMsg, setErrorMsg] = useState('');
  const [capturedImageUrl, setCapturedImageUrl] = useState<string | null>(null);

  useEffect(() => () => { if (capturedImageUrl) URL.revokeObjectURL(capturedImageUrl); }, [capturedImageUrl]);

  const handleStart = async () => {
    const enabled = await checkMode();
    if (!enabled) return;
    setStep(moduleType === 'register' ? 'input' : 'capture');
  };
  const handleInputNext = (m: string) => { setMemo(m); setStep('capture'); };

  const handleCapture = async (blob: Blob) => {
    const currentRequestId = generateRequestId(sessionPrefix);
    setRequestId(currentRequestId);
    if (capturedImageUrl) URL.revokeObjectURL(capturedImageUrl);
    setCapturedImageUrl(URL.createObjectURL(blob));
    setStep('submitting');

    /* API 응답에서 data가 null이면 errors 메시지로 예외 변환 */
    function assertData<T>(res: { data: { data: T; errors: { message: string } | null } }): T {
      if (!res.data.data) throw new Error(res.data.errors?.message ?? t('mobile_test.process_error'));
      return res.data.data;
    }

    try {
      if (testMode === 'sdk') {
        if (moduleType === 'register') {
          setResult({ type: 'register', data: assertData(await sdkRegister(token, blob, memo || undefined)) });
        } else if (moduleType === 'verify') {
          const res = await sdkVerify(token, fid, blob, currentRequestId);
          setResult({ type: 'verify', data: res.data.data ?? {
            success: false, faceId: fid, similarity: 0,
            failureType: null, failureReason: res.data.errors?.message ?? null,
            matchingFaceId: '', userDescription: null,
          }});
        } else if (moduleType === 'match') {
          const res = await sdkIdentify(token, blob, currentRequestId);
          setResult({ type: 'match', data: res.data.data ?? {
            success: false, faceId: '', similarity: 0,
            failureType: null, failureReason: res.data.errors?.message ?? null,
            userDescription: null,
          }});
        } else {
          const res = await sdkLiveness(token, blob);
          setResult({ type: 'liveness', data: res.data.data ?? {
            success: false, transactionUuid: '',
            failureReason: res.data.errors?.message ?? null,
          }});
        }
      } else {
        if (moduleType === 'register') {
          setResult({ type: 'register', data: assertData(await demoRegister(apiKey, blob, memo || undefined)) });
        } else if (moduleType === 'verify') {
          setResult({ type: 'verify', data: assertData(await demoVerify(apiKey, fid, blob, currentRequestId)) });
        } else if (moduleType === 'match') {
          setResult({ type: 'match', data: assertData(await demoIdentify(apiKey, blob, currentRequestId)) });
        } else {
          setResult({ type: 'liveness', data: assertData(await demoLiveness(apiKey, blob)) });
        }
      }
      setStep('result');
    } catch (err) {
      const apiMessage = axios.isAxiosError(err)
        ? (err.response?.data as { errors?: { message?: string } })?.errors?.message
        : err instanceof Error ? err.message : undefined;
      setErrorMsg(apiMessage ?? t('mobile_test.process_error'));
      setStep('error');
    }
  };

  const handleRetry = async () => {
    setResult(null); setErrorMsg(''); setMemo('');
    const enabled = await checkMode();
    if (enabled) setStep('welcome');
  };

  /* 확인 중 */
  if ((apiKey || token) && modeEnabled === null) {
    return (
      <MobileLayout>
        <div className="flex-1 flex items-center justify-center">
          <div className="w-8 h-8 border-4 border-[#e2e8f0] border-t-[#006fff] rounded-full animate-spin" />
        </div>
      </MobileLayout>
    );
  }

  /* 모드 비활성화 */
  if (modeEnabled === false) {
    return (
      <MobileLayout>
        <main className="flex flex-1 flex-col px-6 pt-8 min-h-0" style={{ paddingBottom: 'calc(32px + env(safe-area-inset-bottom))' }}>
          <div className="w-full flex flex-col flex-1 min-h-0">
            <ModeDisabledScreen mode={testMode === 'sdk' ? 'token' : 'demo'} projectName={projectName} apiKey={apiKey} token={token} moduleMeta={meta} />
          </div>
        </main>
      </MobileLayout>
    );
  }

  /* API 키 또는 토큰 없음 */
  if (!apiKey && !token) {
    return (
      <MobileLayout>
        <div className="flex-1 flex flex-col items-center justify-center px-6 gap-4">
          <div className="w-12 h-12 rounded-full bg-[#fee2e2] flex items-center justify-center">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#ef4444" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
          </div>
          <p className="text-[15px] text-[#475569] text-center leading-relaxed tracking-[-0.35px] whitespace-pre-line">
            {t('mobile_test.invalid_link')}
          </p>
        </div>
      </MobileLayout>
    );
  }

  const isResultStep = step === 'result';
  const isCaptureStep = step === 'capture';

  return (
    <MobileLayout bg={isResultStep ? '#f5f6f8' : '#ffffff'}>
      {isCaptureStep ? (
        /* 촬영 화면: 패딩 없이 풀 높이 */
        <main className="flex flex-1 flex-col min-h-0">
          <CaptureScreen requestId={requestId} apiKey={apiKey} token={token} moduleType={moduleType} onCapture={handleCapture} />
        </main>
      ) : (
        /* 나머지 화면: 패딩 + flex-col 채우기 */
        <main
          className="flex flex-1 flex-col px-6 pt-8 min-h-0"
          style={{ paddingBottom: 'calc(32px + env(safe-area-inset-bottom))' }}
        >
          <div className="w-full flex flex-col flex-1 min-h-0">
            {step === 'welcome' && <WelcomeScreen type={moduleType} meta={meta} projectName={projectName} isTokenMode={testMode === 'sdk'} onStart={handleStart} />}
            {step === 'input' && <InputScreen onNext={handleInputNext} />}
            {step === 'submitting' && <SubmittingScreen />}
            {step === 'result' && result && (
              result.type === 'register' ? (
                <RegisterResultScreen data={result.data} capturedImageUrl={capturedImageUrl} requestId={requestId} onRetry={handleRetry} />
              ) : result.type === 'verify' ? (
                <VerifyResultScreen result={result} capturedImageUrl={capturedImageUrl} requestId={requestId} fid={fid} onRetry={handleRetry} />
              ) : result.type === 'match' ? (
                <MatchResultScreen result={result} capturedImageUrl={capturedImageUrl} requestId={requestId} onRetry={handleRetry} />
              ) : (
                <LivenessResultScreen result={result} onRetry={handleRetry} />
              )
            )}
            {step === 'error' && <ErrorScreen message={errorMsg} onRetry={handleRetry} />}
          </div>
        </main>
      )}
    </MobileLayout>
  );
}

export default MobileTestPage;

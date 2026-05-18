"use client";
import { RefObject, MutableRefObject } from "react";
import { User } from "lucide-react";
import { translations } from "@/lib/translations";

type Tx = typeof translations["ko"];
type ScanStatus = "idle" | "scanning" | "complete";

interface Props {
  referenceImage: string | null;
  scanStatus: ScanStatus;
  scanProgress: number;
  isLoading: boolean;
  apiError: string | null;
  cameraError: string | null;
  modelReady: boolean;
  notFrontal: boolean;
  faceClipped: boolean;
  faceTooFar: boolean;
  multipleFaces: boolean;
  captureMode: "auto" | "manual";
  captureModeRef: MutableRefObject<"auto" | "manual">;
  videoRef: RefObject<HTMLVideoElement | null>;
  tx: Tx;
  lang: string;
  onSetCaptureMode: (m: "auto" | "manual") => void;
  onManualCapture: () => void;
}

export function FaceVerifySection({
  referenceImage, scanStatus, scanProgress, isLoading, apiError, cameraError,
  modelReady, notFrontal, faceClipped, faceTooFar, multipleFaces,
  captureMode, captureModeRef, videoRef, tx, lang,
  onSetCaptureMode, onManualCapture,
}: Props) {
  const frameBorderColor =
    scanStatus === "complete" ? "#10b981" :
    scanStatus === "scanning" ? "#3b82f6" :
    "rgba(255,255,255,0.2)";

  const frameGlow =
    scanStatus === "complete" ? "0 0 40px rgba(16,185,129,0.35)" :
    scanStatus === "scanning" ? "0 0 24px rgba(59,130,246,0.4)" :
    "none";

  return (
    <div className={`${referenceImage ? "mt-24" : "mt-0"} flex flex-col items-center gap-5 animate-fade-in w-full px-8`}>

      {/* Guide text */}
      {scanStatus !== "complete" && !isLoading && (
        <div className="text-center space-y-0.5 min-h-[36px]">
          <p className="text-white font-semibold text-sm">
            {!modelReady ? tx.aiModelPreparing : multipleFaces ? tx.multipleFaces : faceTooFar ? tx.faceTooFar : faceClipped ? tx.faceClipped : notFrontal ? tx.faceFront : tx.alignFace}
          </p>
          <p className="text-white/40 text-[11px] font-medium">
            {!modelReady ? tx.pleaseWait : multipleFaces ? tx.multipleFacesSub : faceTooFar ? tx.faceTooFarSub : faceClipped ? tx.faceClippedSub : notFrontal ? tx.lookStraight : captureMode === "manual" ? (lang === "ko" ? "버튼을 눌러 직접 촬영하세요" : "Press the button to capture") : tx.autoVerify}
          </p>
        </div>
      )}

      {/* Capture mode toggle */}
      {scanStatus !== "complete" && !isLoading && (
        <div className="flex items-center bg-white/10 rounded-full p-1 gap-0.5">
          <button
            onClick={() => { onSetCaptureMode("auto"); captureModeRef.current = "auto"; }}
            className={`flex items-center gap-1.5 px-4 py-1.5 rounded-full text-[12px] font-semibold transition-all ${captureMode === "auto" ? "bg-white text-slate-800 shadow" : "text-white/50"}`}
          >
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 2a10 10 0 1 0 10 10"/><path d="M12 6v6l4 2"/>
            </svg>
            {lang === "ko" ? "자동 촬영" : "Auto"}
          </button>
          <button
            onClick={() => { onSetCaptureMode("manual"); captureModeRef.current = "manual"; }}
            className={`flex items-center gap-1.5 px-4 py-1.5 rounded-full text-[12px] font-semibold transition-all ${captureMode === "manual" ? "bg-white text-slate-800 shadow" : "text-white/50"}`}
          >
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="3"/><circle cx="12" cy="12" r="9"/>
            </svg>
            {lang === "ko" ? "수동 촬영" : "Manual"}
          </button>
        </div>
      )}

      {/* Camera circle */}
      <div className="relative w-full aspect-square max-w-[320px]">
        <div className="absolute inset-0 rounded-full bg-slate-900 border-4 border-white/10 shadow-2xl overflow-hidden">
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,rgba(37,99,235,0.15)_0%,rgba(2,6,23,1)_100%)]" />
          {scanStatus === "idle" && !cameraError && (
            <svg className="absolute inset-0 w-full h-full pointer-events-none z-20" viewBox="0 0 320 320">
              <circle cx="160" cy="160" r="154" fill="none"
                stroke="rgba(255,255,255,0.28)" strokeWidth="2" strokeDasharray="10 7">
                <animateTransform attributeName="transform" type="rotate"
                  from="0 160 160" to="360 160 160" dur="20s" repeatCount="indefinite"/>
              </circle>
            </svg>
          )}
          {!cameraError && scanStatus !== "complete" && (
            <video ref={videoRef} autoPlay playsInline muted
              className="absolute inset-0 w-full h-full object-cover"
              style={{ transform: "scaleX(-1)" }}
            />
          )}
          <div className="absolute inset-0 rounded-full border-2 transition-all duration-500 pointer-events-none"
            style={{ borderColor: frameBorderColor, boxShadow: frameGlow }}
          />
          {scanStatus === "scanning" && (
            <div className="absolute inset-x-0 h-px animate-scanning-line z-10"
              style={{ background: "linear-gradient(to right, transparent, #60a5fa, transparent)", boxShadow: "0 0 20px rgba(96,165,250,1)" }}
            />
          )}
          {scanStatus === "complete" && (
            <div className="absolute inset-0 bg-emerald-900/60 flex flex-col items-center justify-center gap-4">
              <div className="w-20 h-20 rounded-full bg-emerald-500/25 flex items-center justify-center">
                <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#10b981" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><polyline points="22 4 12 14.01 9 11.01" />
                </svg>
              </div>
              <span className="font-semibold text-base tracking-wide text-emerald-400">{tx.verifiedLabel}</span>
            </div>
          )}
          {cameraError && (
            <div className="absolute inset-0 flex flex-col items-center justify-center px-6 text-center">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.3)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="mb-3">
                <path d="M23 7 16 12 23 17V7Z" /><rect width="15" height="14" x="1" y="5" rx="2" ry="2" />
                <line x1="1" y1="1" x2="23" y2="23" />
              </svg>
              <p className="text-white/40 text-xs font-medium leading-relaxed whitespace-pre-line">{cameraError}</p>
            </div>
          )}
        </div>
      </div>

      {/* Scanning progress */}
      {scanStatus === "scanning" && (
        <div className="w-full flex flex-col items-center gap-2">
          <div className="w-full h-1 rounded-full overflow-hidden bg-white/10">
            <div className="h-full rounded-full transition-all duration-100"
              style={{ width: `${scanProgress}%`, background: "linear-gradient(to right, #3b82f6, #60a5fa)" }}
            />
          </div>
        </div>
      )}

      {/* Manual shutter button */}
      {scanStatus !== "complete" && !isLoading && captureMode === "manual" && (
        <button
          onClick={onManualCapture}
          className="w-16 h-16 rounded-full bg-white flex items-center justify-center active:scale-90 transition-transform shadow-lg"
        >
          <div className="w-12 h-12 rounded-full bg-white border-[3px] border-slate-200" />
        </button>
      )}

      {!modelReady && (
        <div className="flex items-center gap-2">
          <User className="w-4 h-4 text-teal-400/60" />
          <p className="text-white/40 text-[13px] font-semibold">{tx.faceCameraDirectly}</p>
        </div>
      )}

      {scanStatus === "complete" && !apiError && (
        <div className="w-full">
          <div className={`border rounded-2xl px-5 py-4 text-center ${isLoading ? "bg-blue-500/10 border-blue-500/20" : "bg-emerald-500/10 border-emerald-500/20"}`}>
            {isLoading ? (
              <div className="flex items-center justify-center gap-3">
                <div className="w-4 h-4 rounded-full border-2 border-blue-400/30 border-t-blue-400 animate-spin shrink-0" />
                <p className="text-blue-400 font-semibold text-[15px] tracking-[-0.375px]">{tx.analyzingFace}</p>
              </div>
            ) : (
              <p className="text-emerald-400 font-semibold text-[15px] tracking-[-0.375px]">{tx.verifiedRedirecting}</p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

"use client";
import { RefObject } from "react";
import { translations } from "@/lib/translations";

type Tx = typeof translations["ko"];
type ScanStatus = "idle" | "scanning" | "complete";

interface Props {
  inputMethod: "camera" | "file";
  capturedImage: string | null;
  videoRef: RefObject<HTMLVideoElement | null>;
  scanStatus: ScanStatus;
  scanProgress: number;
  cameraError: string | null;
  modelReady: boolean;
  notFrontal: boolean;
  faceClipped: boolean;
  faceTooFar: boolean;
  multipleFaces: boolean;
  uploadNoFace: boolean;
  tx: Tx;
  onReset: () => void;
  onUploadClick: () => void;
  onMethodChange: (method: "camera" | "file") => void;
}

export function FaceScannerViewport({
  inputMethod, capturedImage, videoRef, scanStatus, scanProgress,
  cameraError, modelReady, notFrontal, faceClipped, faceTooFar,
  multipleFaces, uploadNoFace, tx, onReset, onUploadClick, onMethodChange,
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
    <section className="w-full">
      <style>{`
        @keyframes scanMove {
          0%   { top: 0;    opacity: 0; }
          15%  { opacity: 1; }
          85%  { opacity: 1; }
          100% { top: 100%; opacity: 0; }
        }
        .animate-scanning-line { animation: scanMove 2s infinite linear; }
      `}</style>
      <div className="relative bg-slate-950 rounded-[3rem] overflow-hidden shadow-2xl border-4 border-white aspect-[4/5]">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,rgba(37,99,235,0.15)_0%,rgba(2,6,23,1)_100%)]" />

        <div className="relative w-full h-full flex flex-col items-center justify-center p-8">
          {inputMethod === "camera" ? (
            <div className="relative w-full h-full max-w-[280px] max-h-[360px] flex items-center justify-center">
              {scanStatus === "idle" && !cameraError && (
                <svg className="absolute inset-0 w-full h-full pointer-events-none z-20" viewBox="0 0 280 360" preserveAspectRatio="none">
                  <ellipse cx="140" cy="180" rx="136" ry="176" fill="none"
                    stroke="rgba(255,255,255,0.28)" strokeWidth="2" strokeDasharray="10 7">
                    <animateTransform attributeName="transform" type="rotate"
                      from="0 140 180" to="360 140 180" dur="20s" repeatCount="indefinite"/>
                  </ellipse>
                </svg>
              )}
              <div
                className="absolute inset-0 border-2 overflow-hidden transition-all duration-500"
                style={{ borderRadius: "50%", borderColor: frameBorderColor, boxShadow: frameGlow }}
              >
                {!cameraError && (
                  <video
                    ref={videoRef}
                    autoPlay
                    playsInline
                    muted
                    className="absolute inset-0 w-full h-full object-cover"
                    style={{ transform: "scaleX(-1)", visibility: capturedImage ? "hidden" : "visible" }}
                  />
                )}
                {capturedImage && (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={capturedImage} alt="캡처된 얼굴" className="absolute inset-0 w-full h-full object-cover" />
                )}
                {cameraError && (
                  <div className="absolute inset-0 flex flex-col items-center justify-center px-6 text-center">
                    <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.3)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="mb-3">
                      <path d="M23 7 16 12 23 17V7Z"/><rect width="15" height="14" x="1" y="5" rx="2" ry="2"/>
                      <line x1="1" y1="1" x2="23" y2="23" />
                    </svg>
                    <p className="text-white/40 text-xs font-medium leading-relaxed whitespace-pre-line">{cameraError}</p>
                  </div>
                )}
                {scanStatus === "scanning" && (
                  <div className="absolute inset-x-0 h-px animate-scanning-line"
                    style={{ background: "linear-gradient(to right, transparent, #60a5fa, transparent)", boxShadow: "0 0 20px rgba(96,165,250,1)" }}
                  />
                )}
                {scanStatus === "complete" && (
                  <div className="absolute inset-0 flex flex-col items-center justify-center" style={{ background: "rgba(0,0,0,0.38)" }}>
                    <div className="w-20 h-20 rounded-full flex items-center justify-center mb-4" style={{ background: "rgba(16,185,129,0.25)" }}>
                      <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#10b981" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><polyline points="22 4 12 14.01 9 11.01" />
                      </svg>
                    </div>
                    <span className="font-semibold text-base tracking-wide" style={{ color: "#10b981" }}>{tx.analysisComplete}</span>
                  </div>
                )}
              </div>
            </div>
          ) : (
            capturedImage ? (
              <div className="relative w-full h-full max-w-[280px] max-h-[360px] flex items-center justify-center">
                <div className="absolute inset-0 border-2 rounded-[100px] overflow-hidden"
                  style={{ borderColor: uploadNoFace ? "#f59e0b" : "#10b981", boxShadow: uploadNoFace ? "0 0 40px rgba(245,158,11,0.35)" : "0 0 40px rgba(16,185,129,0.35)" }}
                >
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={capturedImage} alt="업로드된 이미지" className="absolute inset-0 w-full h-full object-cover" />
                  <div className="absolute inset-0 flex flex-col items-center justify-center" style={{ background: "rgba(0,0,0,0.45)" }}>
                    {uploadNoFace ? (
                      <>
                        <div className="w-20 h-20 rounded-full flex items-center justify-center mb-4" style={{ background: "rgba(245,158,11,0.25)" }}>
                          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#f59e0b" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <circle cx="12" cy="12" r="10" /><path d="M12 8v4" /><path d="M12 16h.01" />
                          </svg>
                        </div>
                        <span className="font-semibold text-base tracking-wide text-center px-4" style={{ color: "#f59e0b" }}>{tx.uploadNoFace}</span>
                      </>
                    ) : (
                      <>
                        <div className="w-20 h-20 rounded-full flex items-center justify-center mb-4" style={{ background: "rgba(16,185,129,0.25)" }}>
                          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#10b981" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><polyline points="22 4 12 14.01 9 11.01" />
                          </svg>
                        </div>
                        <span className="font-semibold text-base tracking-wide" style={{ color: "#10b981" }}>{tx.analysisComplete}</span>
                      </>
                    )}
                  </div>
                </div>
              </div>
            ) : (
              <div className="flex flex-col items-center z-10">
                <div className="w-20 h-20 rounded-[2rem] flex items-center justify-center mb-6" style={{ background: "rgba(255,255,255,0.05)", border: "1px solid rgba(255,255,255,0.1)" }}>
                  <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.3)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                    <polyline points="17 8 12 3 7 8" />
                    <line x1="12" y1="3" x2="12" y2="15" />
                  </svg>
                </div>
                <button onClick={onUploadClick} className="bg-white text-slate-900 px-8 py-3.5 rounded-2xl font-semibold text-sm shadow-xl">
                  {tx.selectImage}
                </button>
              </div>
            )
          )}

          {/* Status messages & progress */}
          <div className="absolute bottom-24 left-0 right-0 flex flex-col items-center gap-3 z-10 px-8">
            {inputMethod === "camera" && scanStatus === "idle" && !cameraError && (
              <div className="text-center space-y-1">
                <p className="text-white font-semibold text-base">
                  {!modelReady ? tx.aiModelPreparing : multipleFaces ? tx.multipleFaces : faceTooFar ? tx.faceTooFar : faceClipped ? tx.faceClipped : notFrontal ? tx.faceFront : tx.alignFace}
                </p>
                <p className="text-[11px] font-medium" style={{ color: "rgba(255,255,255,0.4)" }}>
                  {!modelReady ? tx.pleaseWait : multipleFaces ? tx.multipleFacesSub : faceTooFar ? tx.faceTooFarSub : faceClipped ? tx.faceClippedSub : notFrontal ? tx.lookStraight : tx.scanAutoComplete}
                </p>
              </div>
            )}
            {inputMethod === "camera" && scanStatus === "scanning" && (
              <div className="w-full flex flex-col items-center gap-2">
                <p className="text-white font-semibold text-base flex items-center gap-2">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#60a5fa" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M12 3l1.912 5.813a2 2 0 0 0 1.275 1.275L21 12l-5.813 1.912a2 2 0 0 0-1.275 1.275L12 21l-1.912-5.813a2 2 0 0 0-1.275-1.275L3 12l5.813-1.912a2 2 0 0 0 1.275-1.275L12 3z" />
                  </svg>
                  {tx.scanningFeatures}
                </p>
                <div className="w-full h-1 rounded-full overflow-hidden" style={{ background: "rgba(255,255,255,0.1)" }}>
                  <div className="h-full rounded-full transition-all duration-100"
                    style={{ width: `${scanProgress}%`, background: "linear-gradient(to right, #3b82f6, #60a5fa)" }}
                  />
                </div>
                <p className="text-[11px] font-medium" style={{ color: "rgba(255,255,255,0.4)" }}>{tx.holdFront}</p>
              </div>
            )}
            {scanStatus === "complete" && inputMethod === "camera" && (
              <button onClick={onReset}
                className="flex items-center gap-2 font-semibold text-xs px-5 py-2.5 rounded-full transition-all active:scale-95"
                style={{ background: "rgba(255,255,255,0.1)", color: "rgba(255,255,255,0.5)", border: "1px solid rgba(255,255,255,0.1)" }}
              >
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="23 4 23 10 17 10" /><polyline points="1 20 1 14 7 14" />
                  <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
                </svg>
                {tx.rescan}
              </button>
            )}
            {inputMethod === "file" && capturedImage && (
              <button onClick={onUploadClick}
                className="flex items-center gap-2 font-semibold text-xs px-5 py-2.5 rounded-full transition-all active:scale-95"
                style={{ background: "rgba(255,255,255,0.1)", color: "rgba(255,255,255,0.5)", border: "1px solid rgba(255,255,255,0.1)" }}
              >
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                  <polyline points="17 8 12 3 7 8" /><line x1="12" y1="3" x2="12" y2="15" />
                </svg>
                {tx.changeImage}
              </button>
            )}
          </div>

          {/* Camera / Upload toggle */}
          <div className="absolute bottom-6 left-1/2 -translate-x-1/2 rounded-2xl p-1 flex gap-1 z-10"
            style={{ background: "rgba(15,23,42,0.6)", backdropFilter: "blur(12px)", border: "1px solid rgba(255,255,255,0.1)" }}
          >
            <button
              onClick={() => onMethodChange("camera")}
              className={`px-5 py-2.5 rounded-xl text-xs font-semibold transition-all ${inputMethod === "camera" ? "bg-white text-slate-900 shadow-lg" : "text-white/40"}`}
            >{tx.camera}</button>
            <button
              onClick={() => onMethodChange("file")}
              className={`px-5 py-2.5 rounded-xl text-xs font-semibold transition-all ${inputMethod === "file" ? "bg-white text-slate-900 shadow-lg" : "text-white/40"}`}
            >{tx.upload}</button>
          </div>
        </div>
      </div>
    </section>
  );
}

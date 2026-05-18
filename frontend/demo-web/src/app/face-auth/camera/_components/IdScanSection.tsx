"use client";
import { RefObject } from "react";
import { Scan } from "lucide-react";
import { translations } from "@/lib/translations";

type Tx = typeof translations["ko"];

interface Props {
  idScanType: "id" | "face";
  idVideoRef: RefObject<HTMLVideoElement | null>;
  videoRef: RefObject<HTMLVideoElement | null>;
  tx: Tx;
  lang: string;
  onSetIdScanType: (t: "id" | "face") => void;
  onCaptureId: () => void;
  onCaptureFace: () => void;
}

export function IdScanSection({ idScanType, idVideoRef, videoRef, tx, lang, onSetIdScanType, onCaptureId, onCaptureFace }: Props) {
  return (
    <div className="flex flex-col items-center gap-6 w-full px-8">
      {/* Toggle: 신분증 / 얼굴 */}
      <div className="flex items-center bg-white/10 rounded-full p-1 gap-0.5">
        <button
          onClick={() => onSetIdScanType("id")}
          className={`flex items-center gap-1.5 px-5 py-1.5 rounded-full text-[12px] font-semibold transition-all ${idScanType === "id" ? "bg-white text-slate-800 shadow" : "text-white/50"}`}
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <rect x="2" y="5" width="20" height="14" rx="2"/><path d="M6 9h4M6 13h8"/>
          </svg>
          {lang === "ko" ? "신분증" : "ID Card"}
        </button>
        <button
          onClick={() => onSetIdScanType("face")}
          className={`flex items-center gap-1.5 px-5 py-1.5 rounded-full text-[12px] font-semibold transition-all ${idScanType === "face" ? "bg-white text-slate-800 shadow" : "text-white/50"}`}
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7"/>
          </svg>
          {lang === "ko" ? "얼굴" : "Face"}
        </button>
      </div>

      {/* 신분증 카메라 뷰 */}
      {idScanType === "id" && (
        <>
          <div className="w-72 h-44 border-2 border-dashed border-white/30 rounded-2xl flex items-center justify-center relative bg-white/5 backdrop-blur-sm">
            <video ref={idVideoRef} autoPlay playsInline muted
              className="absolute inset-0 w-full h-full object-cover rounded-2xl"
              style={{ transform: "none" }}
            />
            <div className="absolute -top-3 -left-3 w-6 h-6 border-t-4 border-l-4 border-blue-500 rounded-tl-lg z-10" />
            <div className="absolute -top-3 -right-3 w-6 h-6 border-t-4 border-r-4 border-blue-500 rounded-tr-lg z-10" />
            <div className="absolute -bottom-3 -left-3 w-6 h-6 border-b-4 border-l-4 border-blue-500 rounded-bl-lg z-10" />
            <div className="absolute -bottom-3 -right-3 w-6 h-6 border-b-4 border-r-4 border-blue-500 rounded-br-lg z-10" />
            <Scan className="w-12 h-12 text-white/20 relative z-10" />
          </div>
          <p className="text-white/60 text-[13px] font-semibold animate-pulse">{tx.fitIdCard}</p>
          <button
            onClick={onCaptureId}
            className="w-16 h-16 rounded-full bg-white flex items-center justify-center active:scale-90 transition-transform shadow-lg"
          >
            <div className="w-12 h-12 rounded-full bg-white border-[3px] border-slate-200" />
          </button>
        </>
      )}

      {/* 얼굴 카메라 뷰 (정원) */}
      {idScanType === "face" && (
        <>
          <p className="text-white/60 text-[13px] font-semibold">
            {lang === "ko" ? "얼굴을 원 안에 맞춰주세요" : "Align your face in the circle"}
          </p>
          <div className="relative w-full aspect-square max-w-[300px]">
            <div className="absolute inset-0 rounded-full bg-slate-900 border-4 border-white/10 shadow-2xl overflow-hidden">
              <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,rgba(37,99,235,0.15)_0%,rgba(2,6,23,1)_100%)]" />
              <svg className="absolute inset-0 w-full h-full pointer-events-none z-20" viewBox="0 0 300 300">
                <circle cx="150" cy="150" r="144" fill="none"
                  stroke="rgba(255,255,255,0.28)" strokeWidth="2" strokeDasharray="10 7">
                  <animateTransform attributeName="transform" type="rotate"
                    from="0 150 150" to="360 150 150" dur="20s" repeatCount="indefinite"/>
                </circle>
              </svg>
              <video ref={videoRef} autoPlay playsInline muted
                className="absolute inset-0 w-full h-full object-cover"
                style={{ transform: "scaleX(-1)" }}
              />
              <div className="absolute inset-0 rounded-full border-2 border-white/20 pointer-events-none" />
            </div>
          </div>
          <button
            onClick={onCaptureFace}
            className="w-16 h-16 rounded-full bg-white flex items-center justify-center active:scale-90 transition-transform shadow-lg"
          >
            <div className="w-12 h-12 rounded-full bg-white border-[3px] border-slate-200" />
          </button>
        </>
      )}
    </div>
  );
}

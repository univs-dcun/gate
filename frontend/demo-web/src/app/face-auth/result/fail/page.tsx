"use client";

import { useRouter } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { useLanguage } from "@/contexts/LanguageContext";
import { translations } from "@/lib/translations";

function FailView() {
  const router = useRouter();
  const [capturedImage, setCapturedImage] = useState<string | null>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const [apiResult, setApiResult] = useState<any>(null);
  const { lang } = useLanguage();
  const tx = translations[lang];

  useEffect(() => {
    const img = sessionStorage.getItem("faceAuthCapturedImage");
    if (img) setCapturedImage(img);
    try {
      const r = sessionStorage.getItem("univsAuthResult");
      if (r) setApiResult(JSON.parse(r));
    } catch {}
  }, []);

  const errorCode: string | null = apiResult?.errors?.code ?? null;

  return (
    <div
      className="min-h-screen bg-[#F2F4F5] flex flex-col"
      style={{ fontFamily: "'Pretendard', -apple-system, BlinkMacSystemFont, system-ui, sans-serif" }}
    >
      {/* Header */}
      <header className="bg-white border-b border-[#e2e8f0] sticky top-0 z-10">
        <div className="w-full h-[60px] flex items-center px-5">
          <div className="flex items-center gap-3">
            <button onClick={() => router.back()} className="w-6 h-6 flex items-center justify-center">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                <path d="M15 18l-6-6 6-6" stroke="#64748B" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </button>
            <span className="text-[16px] font-semibold text-[#64748b] tracking-[-0.4px]">{tx.resultLivenessHeader}</span>
          </div>
        </div>
      </header>

      {/* Photo + title */}
      <div className="bg-white flex flex-col items-center pt-6 pb-5 gap-3 w-full">
        <div className="relative w-[100px] h-[90px]">
          {capturedImage ? (
            <img
              src={capturedImage}
              alt="촬영된 얼굴"
              className="absolute left-[5px] top-0 w-[90px] h-[90px] rounded-full object-cover"
            />
          ) : (
            <div className="absolute left-[5px] top-0 w-[90px] h-[90px] rounded-full bg-[#ccd1d9] flex items-center justify-center">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.5">
                <rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 21V9"/>
              </svg>
            </div>
          )}
          <div className="absolute left-[71px] top-[66px] w-[24px] h-[24px] rounded-full bg-[#ef4444] border-2 border-white flex items-center justify-center">
            <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
              <path d="M2 2l6 6M8 2l-6 6" stroke="white" strokeWidth="1.8" strokeLinecap="round"/>
            </svg>
          </div>
        </div>

        <div className="flex flex-col items-center gap-1 px-5 text-center">
          <p className="text-[16px] font-semibold text-[#ef4444] tracking-[-0.4px] leading-[1.4]">
            {tx.livenessFailTitle}
          </p>
          <p className="text-[13px] text-[#64748b] tracking-[-0.325px] leading-[1.4]">
            {tx.livenessFailSubtitle}
          </p>
        </div>
      </div>

      {/* Reason Analysis card */}
      <div className="w-full px-5 pt-4 pb-[100px]">
        <div className="bg-white rounded-[14px] px-5 py-5 flex flex-col gap-3" style={{ border: "1px solid #fee2e2" }}>
          {/* Card header */}
          <div className="flex items-center gap-1.5">
            <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
              <path d="M8 1.5L2 4v4c0 3.31 2.57 5.83 6 6.5 3.43-.67 6-3.19 6-6.5V4L8 1.5Z" fill="#fef2f2" stroke="#ef4444" strokeWidth="1.3" strokeLinejoin="round"/>
              <path d="M8 5v3.5M8 10.5v.5" stroke="#ef4444" strokeWidth="1.3" strokeLinecap="round"/>
            </svg>
            <span className="text-[11px] font-bold text-[#ef4444] tracking-[0.5px]">{tx.reasonAnalysis}</span>
          </div>

          {/* Error title */}
          <p className="text-[15px] font-semibold text-[#1e293b] tracking-[-0.375px] leading-[1.4]">
            {tx.livenessNotRealFace}
          </p>

          {/* Guide description */}
          <p className="text-[13px] text-[#475569] tracking-[-0.325px] leading-[1.6]">
            {tx.livenessPleaseTryAgain}
          </p>

          {/* Error code */}
          {errorCode && (
            <p className="text-[12px] font-semibold text-[#94a3b8] tracking-[-0.3px]">
              [{errorCode}]
            </p>
          )}
        </div>
      </div>

      {/* Bottom buttons */}
      <div className="fixed bottom-0 bg-[#F2F4F5] pb-6 pt-3 px-5" style={{ left: "50%", transform: "translateX(-50%)", width: "min(500px, 100vw)", boxSizing: "border-box" }}>
        <div className="flex gap-4">
          <button
            onClick={() => router.push("/")}
            className="flex items-center justify-center gap-2 bg-white border border-[#cbd5e1] rounded-[14px] py-3.5 w-[160px] shrink-0 active:scale-[0.97] transition-transform"
          >
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M3 9.5L10 3l7 6.5V17a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9.5Z" stroke="#475569" strokeWidth="1.5" strokeLinejoin="round"/>
              <path d="M7.5 18V13h5v5" stroke="#475569" strokeWidth="1.5" strokeLinejoin="round"/>
            </svg>
            <span className="text-[14px] font-semibold text-[#475569] tracking-[-0.4px] leading-[1.4]">{tx.goHome}</span>
          </button>
          <button
            onClick={() => router.push("/face-auth/camera?mode=liveness")}
            className="flex flex-1 items-center justify-center gap-2 bg-[#006FFF] rounded-[14px] py-3.5 active:scale-[0.97] transition-transform"
          >
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M15 10A5 5 0 1 1 10 5" stroke="white" strokeWidth="1.5" strokeLinecap="round"/>
              <path d="M10 2v4l2.5-2L10 2Z" fill="white"/>
            </svg>
            <span className="text-[14px] font-semibold text-white tracking-[-0.4px] leading-[1.4]">{tx.retakeBtn}</span>
          </button>
        </div>
      </div>
    </div>
  );
}

export default function LivenessFailPage() {
  return (
    <Suspense>
      <FailView />
    </Suspense>
  );
}

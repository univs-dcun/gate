"use client";

import { useRouter } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { useLanguage } from "@/contexts/LanguageContext";
import { useApiKey } from "@/contexts/ApiKeyContext";
import { translations } from "@/lib/translations";

function Success1NView() {
  const router = useRouter();
  const [capturedImage, setCapturedImage] = useState<string | null>(null);
  const [faceImgError, setFaceImgError] = useState(false);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const [authResult, setAuthResult] = useState<any>(null);
  const { lang } = useLanguage();
  const tx = translations[lang];
  const { apiKey } = useApiKey();

  useEffect(() => {
    const img = sessionStorage.getItem("faceAuthCapturedImage");
    if (img) setCapturedImage(img);
    try {
      const r = sessionStorage.getItem("univsAuthResult");
      if (r) setAuthResult(JSON.parse(r));
    } catch {}
  }, []);

  const faceImageSrc = (() => {
    const raw = authResult?.data?.faceImagePath;
    if (!raw) return null;
    try {
      const url = new URL(raw);
      const innerPath = url.searchParams.get("filePath") ?? raw;
      return `/api/file?filePath=${encodeURIComponent(innerPath)}&apiKey=${encodeURIComponent(apiKey)}`;
    } catch {
      return `/api/file?filePath=${encodeURIComponent(raw)}&apiKey=${encodeURIComponent(apiKey)}`;
    }
  })();

  const simPct = (s?: number) => s == null ? null : Math.min(Math.round(s <= 1 ? s * 100 : s), 100);

  const fmtSimilarity = (s?: number) => {
    if (s == null) return "—";
    return `${Math.round(s <= 1 ? s * 100 : s)}%`;
  };

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
            <span className="text-[16px] font-semibold text-[#64748b] tracking-[-0.4px]">{tx.resultMatchingNHeader}</span>
          </div>
        </div>
      </header>

      {/* Photo + message */}
      <div className="bg-white flex flex-col items-center py-5 gap-3 w-full mb-4">
        <div className="flex items-center gap-3">
          {/* 등록된 사진 (왼쪽) */}
          {faceImageSrc && !faceImgError ? (
            <img
              src={faceImageSrc}
              alt="등록된 얼굴"
              className="w-[90px] h-[90px] rounded-full object-cover"
              onError={() => setFaceImgError(true)}
            />
          ) : (
            <div className="w-[90px] h-[90px] rounded-full bg-[#d1d5db] flex items-center justify-center">
              <span className="text-[#6b7280] text-xs font-semibold">{tx.registeredPhoto}</span>
            </div>
          )}
          {/* 촬영한 사진 (오른쪽) + 초록 체크 배지 */}
          <div className="relative w-[90px] h-[90px]">
            {capturedImage ? (
              <img src={capturedImage} alt="촬영된 얼굴" className="w-[90px] h-[90px] rounded-full object-cover" />
            ) : (
              <div className="w-[90px] h-[90px] rounded-full bg-[#ccd1d9]" />
            )}
            <div className="absolute bottom-0 right-0 w-[24px] h-[24px] rounded-[14px] bg-[#0fb981] border border-white flex items-center justify-center">
              <svg width="13" height="10" viewBox="0 0 13 10" fill="none">
                <path d="M1.5 5l3.5 3.5 6.5-7" stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </div>
          </div>
        </div>
        <p className="text-[16px] font-semibold text-[#0fb981] tracking-[-0.4px] leading-[1.4]">{tx.matchSuccess}</p>
      </div>

      {/* Info card */}
      <div className="w-full px-5 pb-[100px]">
        <div className="bg-white rounded-[12px] px-5 py-5 flex flex-col gap-4">

          <div className="flex items-center gap-2">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path d="M8 1.5L2 4v4c0 3.31 2.57 5.83 6 6.5 3.43-.67 6-3.19 6-6.5V4L8 1.5Z" stroke="#056FFF" strokeWidth="1.3" strokeLinejoin="round"/>
              <path d="M5.5 8l1.8 1.8 3.2-3.2" stroke="#056FFF" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            <span className="text-[12px] font-semibold text-[#056fff] tracking-[-0.3px]">{tx.registeredInfo}</span>
          </div>

          <div className="flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.name}</span>
              <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{authResult?.data?.username ?? "—"}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.livenessApplied}</span>
              {authResult?.data?.checkLiveness === true && authResult?.data?.success === true ? (
                <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{tx.realUserVerified}</span>
              ) : (
                <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{tx.livenessNotApplied}</span>
              )}
            </div>
            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between">
                <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.similarity}</span>
                <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{fmtSimilarity(authResult?.data?.similarity)}</span>
              </div>
              {simPct(authResult?.data?.similarity) != null && (
                <div className="h-2 bg-[#F2F4F5] rounded-full overflow-hidden">
                  <div
                    className="h-full rounded-full transition-[width] duration-1000 ease-out"
                    style={{
                      width: `${simPct(authResult.data.similarity)}%`,
                      background: (simPct(authResult.data.similarity) ?? 0) >= 80 ? "#0fb981" : (simPct(authResult.data.similarity) ?? 0) >= 60 ? "#f59e0b" : "#ef4444",
                    }}
                  />
                </div>
              )}
            </div>
            <div className="flex flex-col gap-2">
              <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.faceId}</span>
              <div className="bg-[#F2F4F5] rounded-[8px] px-3 py-2">
                <p className="text-[14px] font-semibold text-[#475569] tracking-[-0.35px] leading-[1.4] break-all">
                  {authResult?.data?.faceId ?? "—"}
                </p>
              </div>
            </div>
            <div className="h-px bg-[#e2e8f0]" />
            <div className="flex items-center justify-between">
              <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.logId}</span>
              <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{authResult?.data?.matchingHistoryId ?? "—"}</span>
            </div>
            <div className="flex flex-col gap-2">
              <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.requestId}</span>
              <div className="bg-[#F2F4F5] rounded-[8px] px-3 py-2">
                <p className="text-[14px] font-semibold text-[#475569] tracking-[-0.35px] leading-[1.4] break-all">
                  {authResult?.data?.transactionUuid ?? "—"}
                </p>
              </div>
            </div>
          </div>
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
            onClick={() => router.push("/face-auth/camera?mode=1n")}
            className="flex flex-1 items-center justify-center gap-2 bg-[#006FFF] rounded-[14px] py-3.5 active:scale-[0.97] transition-transform"
          >
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M2 7.5A1.5 1.5 0 0 1 3.5 6h1.086a1 1 0 0 0 .707-.293l.914-.914A1 1 0 0 1 6.914 4.5h6.172a1 1 0 0 1 .707.293l.914.914A1 1 0 0 0 15.414 6H16.5A1.5 1.5 0 0 1 18 7.5v7A1.5 1.5 0 0 1 16.5 16h-13A1.5 1.5 0 0 1 2 14.5v-7Z" stroke="white" strokeWidth="1.5"/>
              <circle cx="10" cy="11" r="2.5" stroke="white" strokeWidth="1.5"/>
            </svg>
            <span className="text-[14px] font-semibold text-white tracking-[-0.4px] leading-[1.4]">{tx.addMatchN}</span>
          </button>
        </div>
      </div>
    </div>
  );
}

export default function Success1NPage() {
  return (
    <Suspense>
      <Success1NView />
    </Suspense>
  );
}

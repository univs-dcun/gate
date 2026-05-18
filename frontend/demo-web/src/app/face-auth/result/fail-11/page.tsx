"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { useLanguage } from "@/contexts/LanguageContext";
import { translations } from "@/lib/translations";

function Fail11View() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const type = searchParams.get("type") ?? "";
  const isPhotoAuth = type === "photo";
  const [capturedImage, setCapturedImage] = useState<string | null>(null);
  const [referenceImage, setReferenceImage] = useState<string | null>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const [authResult, setAuthResult] = useState<any>(null);
  const [refPhotoSource, setRefPhotoSource] = useState<string>("");
  const { lang } = useLanguage();
  const tx = translations[lang];

  useEffect(() => {
    const img = sessionStorage.getItem("faceAuthCapturedImage");
    if (img) setCapturedImage(img);
    const ref = sessionStorage.getItem("faceAuthReferenceImage");
    if (ref) setReferenceImage(ref);
    const src = sessionStorage.getItem("faceAuthRefPhotoSource") ?? "";
    setRefPhotoSource(src);
    try {
      const r = sessionStorage.getItem("univsAuthResult");
      if (r) setAuthResult(JSON.parse(r));
    } catch {}
  }, []);

  const errorCode = authResult?.errors?.code ?? authResult?.data?.errors?.code ?? "";
  const errorType = authResult?.errors?.type ?? authResult?.data?.errors?.type ?? "";
  const errorMessage = authResult?.errors?.message ?? authResult?.data?.errors?.message ?? "";
  const logId = authResult?.data?.matchingHistoryId ?? authResult?.data?.logId ?? "—";
  const requestId = authResult?.data?.transactionUuid ?? "—";
  const errCodeUpper = errorCode.toUpperCase();
  const isSystemError = errCodeUpper.startsWith("PJ-") || errCodeUpper.startsWith("SYS-") || errCodeUpper.startsWith("SERVER");
  const livenessFailureType = authResult?.data?.failureType ?? "";
  const livenessFailed = livenessFailureType === "FAKE";
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const livenessFailureReason = authResult?.data?.failureReason ?? "";

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
            <span className="text-[16px] font-semibold text-[#64748b] tracking-[-0.4px]">
              {isPhotoAuth ? tx.result11PhotoHeader : tx.result11CameraHeader}
            </span>
          </div>
        </div>
      </header>

      {/* Photo + message */}
      <div className="bg-white flex flex-col items-center py-5 gap-3 w-full">
        {isPhotoAuth ? (
          <div className="flex items-center gap-3">
            {/* 첨부 사진 */}
            {referenceImage ? (
              <div className="flex flex-col items-center gap-1">
                <img src={referenceImage} alt="첨부 사진" className="w-24 h-24 rounded-full object-cover" />
                <span className="text-[11px] text-[#94a3b8] font-medium">{tx.attachedPhoto}</span>
              </div>
            ) : (
              <div className="flex flex-col items-center gap-1">
                <div className="w-24 h-24 rounded-full bg-[#d1d5db] flex items-center justify-center">
                  <span className="text-[#6b7280] text-xs font-semibold">{tx.attachedPhoto}</span>
                </div>
                <span className="text-[11px] text-[#94a3b8] font-medium">{tx.attachedPhoto}</span>
              </div>
            )}
            {/* 촬영 얼굴 + red X badge */}
            <div className="flex flex-col items-center gap-1">
              <div className="relative w-24 h-24">
                {capturedImage ? (
                  <img src={capturedImage} alt="촬영 얼굴" className="w-24 h-24 rounded-full object-cover" />
                ) : (
                  <div className="w-24 h-24 rounded-full bg-[#d1d5db] flex items-center justify-center">
                    <span className="text-[#6b7280] text-xs font-semibold">{tx.capturedFace}</span>
                  </div>
                )}
                <div className="absolute bottom-0 right-0 w-[24px] h-[24px] rounded-full bg-[#ef4444] border-2 border-white flex items-center justify-center">
                  <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                    <path d="M2 2l6 6M8 2l-6 6" stroke="white" strokeWidth="1.8" strokeLinecap="round"/>
                  </svg>
                </div>
              </div>
              <span className="text-[11px] text-[#94a3b8] font-medium">{tx.capturedFace}</span>
            </div>
          </div>
        ) : (
          <div className="relative w-[100px] h-[90px]">
            {capturedImage ? (
              <img src={capturedImage} alt="촬영된 얼굴" className="absolute left-[5px] top-0 w-[90px] h-[90px] rounded-full object-cover" />
            ) : (
              <div className="absolute left-[5px] top-0 w-[90px] h-[90px] rounded-full bg-[#ccd1d9]" />
            )}
            <div className="absolute left-[71px] top-[66px] w-[24px] h-[24px] rounded-[14px] bg-[#ef4444] border border-white flex items-center justify-center">
              <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                <path d="M2 2l6 6M8 2l-6 6" stroke="white" strokeWidth="1.8" strokeLinecap="round"/>
              </svg>
            </div>
          </div>
        )}

        <div className="flex flex-col items-center gap-1">
          <p className="text-[16px] font-semibold text-[#ef4444] tracking-[-0.4px] leading-[1.4]">
            {livenessFailed && !isSystemError ? tx.livenessFailTitle : tx.verificationFail}
          </p>
          <p className="text-[14px] font-medium text-[#64748b] tracking-[-0.35px] leading-[1.4]">
            {livenessFailed && !isSystemError
              ? tx.livenessFailSubtitle
              : isSystemError
              ? tx.systemErrorApology
              : isPhotoAuth
              ? (refPhotoSource === "gallery" ? tx.mismatchSubtitlePhotoGallery : tx.mismatchSubtitlePhotoCamera)
              : tx.mismatchSubtitle}
          </p>
        </div>
      </div>

      {/* Cards */}
      <div className="w-full px-5 pt-4 pb-[100px] flex flex-col gap-4">
        <div
          className="bg-white rounded-[14px] px-5 py-5 flex flex-col gap-3"
          style={(isSystemError || livenessFailed) ? { border: "1px solid #fee2e2" } : undefined}
        >
          <div className="flex items-center gap-1.5">
            <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
              <path d="M8 1.5L2 4v4c0 3.31 2.57 5.83 6 6.5 3.43-.67 6-3.19 6-6.5V4L8 1.5Z" fill="#fef2f2" stroke="#ef4444" strokeWidth="1.3" strokeLinejoin="round"/>
              <path d="M8 5v3.5M8 10.5v.5" stroke="#ef4444" strokeWidth="1.3" strokeLinecap="round"/>
            </svg>
            <span className="text-[11px] font-bold text-[#ef4444] tracking-[0.5px]">{tx.reasonAnalysis}</span>
          </div>

          {livenessFailed && !isSystemError ? (
            /* 라이브니스 실패 우선 레이아웃 */
            <>
              <div className="flex flex-col gap-1">
                <p className="text-[15px] font-semibold text-[#1e293b] tracking-[-0.375px] leading-[1.4]">{tx.livenessNotRealFace}</p>
                <p className="text-[13px] text-[#475569] tracking-[-0.325px] leading-[1.6]">
                  {tx.livenessPleaseTryAgain}
                </p>
              </div>
              <div className="h-px bg-[#e2e8f0]" />
              <div className="flex items-center justify-between">
                <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.livenessApplied}</span>
                <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{tx.livenessAppliedFail}</span>
              </div>
              <div className="h-px bg-[#e2e8f0]" />
              {livenessFailureType ? (
                <div className="flex items-center justify-between">
                  <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px]">{tx.responseCode}</span>
                  <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px]">{livenessFailureType}</span>
                </div>
              ) : null}
              <div className="flex items-center justify-between">
                <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px]">{tx.logId}</span>
                <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px]">{logId}</span>
              </div>
              <div className="flex flex-col gap-2">
                <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px]">{tx.requestId}</span>
                <div className="bg-[#F2F4F5] rounded-[8px] px-3 py-2">
                  <p className="text-[14px] font-semibold text-[#475569] tracking-[-0.35px] leading-[1.4] break-all">{requestId}</p>
                </div>
              </div>
            </>
          ) : (
            /* 기존 레이아웃 (시스템 오류 or 라이브니스 미적용) */
            <>
              <div className="flex flex-col gap-2">
                <p className="text-[15px] font-semibold text-[#1e293b] tracking-[-0.375px] leading-[1.4]">
                  {isSystemError ? tx.systemErrorTitle : tx.identityFailed}
                </p>
                <div className="flex flex-col gap-1">
                  <p className="text-[13px] text-[#475569] tracking-[-0.325px] leading-[1.6]">
                    {isSystemError
                      ? errorMessage
                      : isPhotoAuth
                      ? (refPhotoSource === "gallery" ? tx.capturedDiffersMsgPhotoGallery : tx.capturedDiffersMsgPhotoCamera)
                      : tx.capturedDiffersMsg}
                  </p>
                  {errorCode ? (
                    <p className="text-[12px] font-semibold text-[#94a3b8] tracking-[-0.3px]">
                      [{errorCode}{errorType ? ` / ${errorType}` : ""}]
                    </p>
                  ) : null}
                </div>
              </div>
              <div className="h-px bg-[#e2e8f0]" />
              <div className="flex items-center justify-between">
                <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.livenessApplied}</span>
                <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{tx.livenessNotApplied}</span>
              </div>
              <div className="h-px bg-[#e2e8f0]" />
              <div className="flex items-center justify-between">
                <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px]">{tx.logId}</span>
                <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px]">{logId}</span>
              </div>
              <div className="flex flex-col gap-2">
                <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px]">{tx.requestId}</span>
                <div className="bg-[#F2F4F5] rounded-[8px] px-3 py-2">
                  <p className="text-[14px] font-semibold text-[#475569] tracking-[-0.35px] leading-[1.4] break-all">{requestId}</p>
                </div>
              </div>
            </>
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
            onClick={() => {
              if (isPhotoAuth) {
                router.push("/face-auth/camera?type=photo");
              } else {
                router.push("/face-auth");
              }
            }}
            className="flex flex-1 items-center justify-center gap-2 bg-[#006FFF] rounded-[14px] py-3.5 active:scale-[0.97] transition-transform"
          >
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M15 10A5 5 0 1 1 10 5" stroke="white" strokeWidth="1.5" strokeLinecap="round"/>
              <path d="M10 2v4l2.5-2L10 2Z" fill="white"/>
            </svg>
            <span className="text-[14px] font-semibold text-white tracking-[-0.4px] leading-[1.4]">{tx.retake}</span>
          </button>
        </div>
      </div>
    </div>
  );
}

export default function Fail11Page() {
  return (
    <Suspense>
      <Fail11View />
    </Suspense>
  );
}

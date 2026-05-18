"use client";

import { useState, useEffect, useRef, useCallback, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ChevronLeft, SwitchCamera } from "lucide-react";
import { useLanguage } from "@/contexts/LanguageContext";
import { useApiKey } from "@/contexts/ApiKeyContext";
import { translations } from "@/lib/translations";
import { requestFaceApi, verifyByImages, ApiError, cacheLivenessEnabled } from "@/lib/api";
import { SelectView } from "./_components/SelectView";
import { IdScanSection } from "./_components/IdScanSection";
import { FaceVerifySection } from "./_components/FaceVerifySection";
import { ApiErrorSheet } from "@/components/ApiErrorSheet";

const SCAN_HOLD_MS = 2000;
type ScanStatus = "idle" | "scanning" | "complete";
type AuthPhase = "id-scan" | "id-captured" | "face-verify";
type View = "select" | "auth";

function CameraView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const userName = searchParams.get("name") ?? "";
  const faceId = searchParams.get("faceId") ?? "";
  const mode = searchParams.get("mode") ?? "1:1";
  const type = searchParams.get("type") ?? "";
  const { lang } = useLanguage();
  const tx = translations[lang];
  const { logout, livenessEnabled } = useApiKey();

  const isPhotoAuth = type === "photo";
  const skipSelect = mode === "1n" || mode === "liveness" || (mode === "1:1" && !isPhotoAuth);
  const [view, setView] = useState<View>(skipSelect ? "auth" : "select");
  const [authPhase, setAuthPhase] = useState<AuthPhase>(skipSelect ? "face-verify" : "id-scan");
  const [cameraFacing, setCameraFacing] = useState<"front" | "back">("back");
  const [isFlashing, setIsFlashing] = useState(false);
  const [referenceImage, setReferenceImage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [apiError, setApiError] = useState<ApiError["type"] | null>(null);

  const [scanStatus, setScanStatus] = useState<ScanStatus>("idle");
  const [scanProgress, setScanProgress] = useState(0);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [modelReady, setModelReady] = useState(false);
  const [notFrontal, setNotFrontal] = useState(false);
  const [faceClipped, setFaceClipped] = useState(false);
  const [faceTooFar, setFaceTooFar] = useState(false);
  const [multipleFaces, setMultipleFaces] = useState(false);
  const [captureMode, setCaptureMode] = useState<"auto" | "manual">("auto");
  const captureModeRef = useRef<"auto" | "manual">("auto");
  const [idScanType, setIdScanType] = useState<"id" | "face">("id");

  const videoRef = useRef<HTMLVideoElement>(null);
  const idVideoRef = useRef<HTMLVideoElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const rafRef = useRef<number>(0);
  const scanStatusRef = useRef<ScanStatus>("idle");
  const scanStartRef = useRef<number | null>(null);
  const startCameraIdRef = useRef(0);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const faceDetectorRef = useRef<any>(null);

  // ── MediaPipe init ──
  useEffect(() => {
    let closed = false;
    (async () => {
      try {
        const { FaceDetector, FilesetResolver } = await import("@mediapipe/tasks-vision");
        const vision = await FilesetResolver.forVisionTasks(
          "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@latest/wasm"
        );
        const detector = await FaceDetector.createFromOptions(vision, {
          baseOptions: {
            modelAssetPath:
              "https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/1/blaze_face_short_range.tflite",
            delegate: "GPU",
          },
          runningMode: "VIDEO",
          minDetectionConfidence: 0.6,
          minSuppressionThreshold: 0.3,
        });
        if (!closed) { faceDetectorRef.current = detector; setModelReady(true); }
        else detector.close();
      } catch (e) { console.error("FaceDetector init failed:", e); }
    })();
    return () => { closed = true; faceDetectorRef.current?.close(); };
  }, []);

  // ── Camera helpers ──
  const stopCamera = () => {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
    if (idVideoRef.current) idVideoRef.current.srcObject = null;
  };

  const startCamera = useCallback(async (facing: "front" | "back") => {
    const callId = ++startCameraIdRef.current;
    setCameraError(null);
    stopCamera();
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: facing === "front" ? "user" : "environment", width: { ideal: 1280 }, height: { ideal: 720 } },
      });
      if (callId !== startCameraIdRef.current) {
        stream.getTracks().forEach((t) => t.stop());
        return;
      }
      streamRef.current = stream;
      const target = facing === "front" ? videoRef.current : idVideoRef.current;
      if (target) target.srcObject = stream;
    } catch (err) {
      if (callId !== startCameraIdRef.current) return;
      const e = err as DOMException;
      if (e.name === "NotAllowedError" || e.name === "PermissionDeniedError") {
        setCameraError("cameraPermissionDenied");
      } else if (e.name === "NotFoundError") {
        setCameraError("cameraNotFound");
      } else {
        setCameraError("cameraUnavailable");
      }
    }
  }, []);

  useEffect(() => {
    if (view !== "auth" || authPhase !== "id-scan") return;
    startCamera("back");
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view, authPhase]);

  const captureIdPhoto = useCallback(() => {
    setIsFlashing(true);
    setTimeout(() => setIsFlashing(false), 300);
    const idVideo = idVideoRef.current;
    if (idVideo && idVideo.videoWidth > 0) {
      const canvas = document.createElement("canvas");
      const cropSize = Math.min(idVideo.videoWidth, idVideo.videoHeight);
      const cx = (idVideo.videoWidth - cropSize) / 2;
      const cy = (idVideo.videoHeight - cropSize) / 2;
      canvas.width = 640; canvas.height = 640;
      const ctx = canvas.getContext("2d");
      if (ctx) {
        ctx.drawImage(idVideo, cx, cy, cropSize, cropSize, 0, 0, 640, 640);
        const dataUrl = canvas.toDataURL("image/jpeg", 0.85);
        sessionStorage.setItem("faceAuthReferenceImage", dataUrl);
        sessionStorage.setItem("faceAuthRefPhotoSource", "camera");
        setReferenceImage(dataUrl);
      }
    }
    setAuthPhase("id-captured");
    stopCamera();
    setTimeout(() => {
      setAuthPhase("face-verify");
      setCameraFacing("front");
      startCamera("front");
    }, 900);
  }, [startCamera]);

  const captureFaceAsReference = useCallback(() => {
    const video = videoRef.current;
    if (!video || video.videoWidth === 0) return;
    setIsFlashing(true);
    setTimeout(() => setIsFlashing(false), 300);
    const vw = video.videoWidth;
    const vh = video.videoHeight;
    const sSize = Math.min(vw, vh);
    const canvas = document.createElement("canvas");
    canvas.width = 640; canvas.height = 640;
    const ctx = canvas.getContext("2d");
    if (ctx) {
      ctx.translate(640, 0); ctx.scale(-1, 1);
      ctx.drawImage(video, (vw - sSize) / 2, (vh - sSize) / 2, sSize, sSize, 0, 0, 640, 640);
      const dataUrl = canvas.toDataURL("image/jpeg", 0.85);
      sessionStorage.setItem("faceAuthReferenceImage", dataUrl);
      sessionStorage.setItem("faceAuthRefPhotoSource", "camera");
      setReferenceImage(dataUrl);
    }
    setAuthPhase("id-captured");
    stopCamera();
    setTimeout(() => {
      setAuthPhase("face-verify");
      setCameraFacing("front");
      startCamera("front");
    }, 900);
  }, [startCamera]);

  const manualCapture = useCallback(() => {
    if (scanStatus === "complete" || isLoading) return;
    const video = videoRef.current;
    if (!video || video.videoWidth === 0) return;
    cancelAnimationFrame(rafRef.current);
    const vw = video.videoWidth;
    const vh = video.videoHeight;
    const sSize = Math.min(vw, vh);
    const canvas = document.createElement("canvas");
    canvas.width = 640; canvas.height = 640;
    const ctx = canvas.getContext("2d");
    if (ctx) {
      ctx.translate(640, 0); ctx.scale(-1, 1);
      ctx.drawImage(video, (vw - sSize) / 2, (vh - sSize) / 2, sSize, sSize, 0, 0, 640, 640);
      sessionStorage.setItem("faceAuthCapturedImage", canvas.toDataURL("image/jpeg", 0.85));
    }
    setIsFlashing(true);
    setTimeout(() => setIsFlashing(false), 500);
    scanStatusRef.current = "complete";
    setScanStatus("complete");
    stopCamera();
  }, [scanStatus, isLoading]);

  // ── Switch camera when idScanType toggles ──
  useEffect(() => {
    if (view !== "auth" || authPhase !== "id-scan") return;
    stopCamera();
    if (idScanType === "face") { setCameraFacing("front"); startCamera("front"); }
    else { setCameraFacing("back"); startCamera("back"); }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idScanType]);

  // ── Start front camera when entering face-verify ──
  useEffect(() => {
    if (view !== "auth" || authPhase !== "face-verify") return;
    startCamera("front");
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view, authPhase]);

  // ── Face detection loop ──
  const runDetection = useCallback(() => {
    const video = videoRef.current;
    const detector = faceDetectorRef.current;
    if (!video || video.readyState < 2 || !detector) {
      rafRef.current = requestAnimationFrame(runDetection);
      return;
    }
    if (scanStatusRef.current === "complete") return;

    try {
      const now = performance.now();
      const result = detector.detectForVideo(video, now);
      const hasFace = result.detections.length > 0 && (result.detections[0].categories[0]?.score ?? 0) >= 0.6;

      if (hasFace) {
        if (result.detections.length > 1) {
          setMultipleFaces(true);
          setFaceClipped(false); setNotFrontal(false); setFaceTooFar(false);
          if (scanStatusRef.current === "scanning") {
            scanStartRef.current = null; scanStatusRef.current = "idle";
            setScanStatus("idle"); setScanProgress(0);
          }
          rafRef.current = requestAnimationFrame(runDetection);
          return;
        }
        setMultipleFaces(false);

        const bb = result.detections[0].boundingBox;
        const vw = video.videoWidth || 1;
        const vh = video.videoHeight || 1;
        const margin = 0.05;
        const inFrame = bb ? bb.originX / vw > margin && bb.originY / vh > margin && (bb.originX + bb.width) / vw < 1 - margin && (bb.originY + bb.height) / vh < 1 - margin : true;
        const isTooFar = bb ? bb.width / vw < 0.2 : false;

        const kps = result.detections[0].keypoints;
        let frontal = true;
        if (kps && kps.length >= 3) {
          const eyeW = Math.abs(kps[0].x - kps[1].x);
          if (eyeW > 0.01) {
            const eyeMidX = (kps[0].x + kps[1].x) / 2;
            frontal = Math.abs(kps[2].x - eyeMidX) < eyeW * 0.35 && Math.abs(kps[0].y - kps[1].y) < eyeW * 0.4;
          }
        }

        const reset = () => { if (scanStatusRef.current === "scanning") { scanStartRef.current = null; scanStatusRef.current = "idle"; setScanStatus("idle"); setScanProgress(0); } };

        if (isTooFar) { setFaceTooFar(true); setFaceClipped(false); setNotFrontal(false); reset(); }
        else if (!inFrame) { setFaceClipped(true); setFaceTooFar(false); setNotFrontal(false); reset(); }
        else if (!frontal) { setNotFrontal(true); setFaceClipped(false); setFaceTooFar(false); reset(); }
        else {
          setFaceTooFar(false); setFaceClipped(false); setNotFrontal(false);
          if (scanStartRef.current === null) {
            scanStartRef.current = now; scanStatusRef.current = "scanning"; setScanStatus("scanning");
          }
          const elapsed = now - scanStartRef.current;
          setScanProgress(Math.min((elapsed / SCAN_HOLD_MS) * 100, 100));
          if (elapsed >= SCAN_HOLD_MS && captureModeRef.current === "auto") {
            if (video.videoWidth > 0) {
              const vw2 = video.videoWidth;
              const vh2 = video.videoHeight;
              let sx: number, sy: number, sSize: number;
              if (bb && bb.width > 0) {
                const pad = 0.35;
                sSize = Math.min(Math.max(bb.width * (1 + 2 * pad), bb.height * (1 + 2 * pad)), vw2, vh2);
                sx = Math.max(0, Math.min(bb.originX + bb.width / 2 - sSize / 2, vw2 - sSize));
                sy = Math.max(0, Math.min(bb.originY + bb.height / 2 - sSize / 2, vh2 - sSize));
              } else {
                sSize = Math.min(vw2, vh2);
                sx = (vw2 - sSize) / 2; sy = (vh2 - sSize) / 2;
              }
              const canvas = document.createElement("canvas");
              canvas.width = 640; canvas.height = 640;
              const ctx = canvas.getContext("2d");
              if (ctx) {
                ctx.translate(640, 0); ctx.scale(-1, 1);
                ctx.drawImage(video, sx, sy, sSize, sSize, 0, 0, 640, 640);
                sessionStorage.setItem("faceAuthCapturedImage", canvas.toDataURL("image/jpeg", 0.85));
              }
            }
            scanStatusRef.current = "complete"; setScanStatus("complete"); stopCamera(); return;
          }
        }
      } else {
        setMultipleFaces(false); setFaceTooFar(false); setFaceClipped(false); setNotFrontal(false);
        if (scanStatusRef.current === "scanning") {
          scanStartRef.current = null; scanStatusRef.current = "idle"; setScanStatus("idle"); setScanProgress(0);
        }
      }
    } catch { /* ignore */ }
    rafRef.current = requestAnimationFrame(runDetection);
  }, []);

  useEffect(() => {
    if (authPhase !== "face-verify" || !modelReady || cameraError) return;
    rafRef.current = requestAnimationFrame(runDetection);
    return () => cancelAnimationFrame(rafRef.current);
  }, [authPhase, modelReady, cameraError, runDetection]);

  // ── API call after capture ──
  useEffect(() => {
    if (scanStatus !== "complete") return;
    const capturedDataUrl = sessionStorage.getItem("faceAuthCapturedImage");
    if (!capturedDataUrl) return;
    setApiError(null); setIsLoading(true);
    const callApi = async () => {
      const t0 = Date.now();
      try {
        const endpoint = mode === "liveness" ? "/liveness" : mode === "1n" ? "/identify" : "/verify";
        let result;
        if (isPhotoAuth) {
          const refImage = sessionStorage.getItem("faceAuthReferenceImage");
          if (!refImage) { setApiError("unknown"); setIsLoading(false); return; }
          result = await verifyByImages(refImage, capturedDataUrl);
        } else if (endpoint === "/verify") {
          result = await requestFaceApi(endpoint, capturedDataUrl, faceId ? { faceId } : undefined);
        } else {
          result = await requestFaceApi(endpoint, capturedDataUrl);
        }
        sessionStorage.setItem("univsAuthResult", JSON.stringify(result));
        sessionStorage.setItem("univsAuthLatency", String(Date.now() - t0));
        if (typeof result?.data?.checkLiveness === "boolean") cacheLivenessEnabled(result.data.checkLiveness);

        if (mode === "liveness") {
          const isLive = result.success === true && result.data?.success === true;
          router.replace(isLive ? "/face-auth/result?mode=liveness" : "/face-auth/result/fail");
        } else if (mode === "1n") {
          const isMatch = result.success === true && result.data?.success === true;
          router.replace(isMatch ? "/face-auth/result/success-1n" : "/face-auth/result/fail-1n");
        } else {
          const isMatch = result.success === true && result.data?.success === true;
          if (isMatch) {
            router.replace(`/face-auth/result?name=${encodeURIComponent(userName)}&mode=${encodeURIComponent(mode)}${type ? `&type=${encodeURIComponent(type)}` : ""}`);
          } else {
            router.replace(`/face-auth/result/fail-11?name=${encodeURIComponent(userName)}${type ? `&type=${encodeURIComponent(type)}` : ""}`);
          }
        }
      } catch (err) {
        if (err instanceof ApiError && err.type === "unauthorized") logout();
        else setApiError(err instanceof ApiError ? err.type : "unknown");
      } finally { setIsLoading(false); }
    };
    callApi();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scanStatus]);

  useEffect(() => {
    return () => { cancelAnimationFrame(rafRef.current); stopCamera(); };
  }, []);

  const retryFromError = useCallback(async () => {
    setApiError(null); setIsLoading(false);
    cancelAnimationFrame(rafRef.current);
    scanStatusRef.current = "idle"; setScanStatus("idle"); setScanProgress(0);
    scanStartRef.current = null;
    await startCamera("front");
    rafRef.current = requestAnimationFrame(runDetection);
  }, [startCamera, runDetection]);

  return (
    <div
      className="min-h-screen bg-slate-950 flex flex-col overflow-hidden"
      style={{ fontFamily: "'Pretendard', -apple-system, BlinkMacSystemFont, system-ui, sans-serif" }}
    >
      <style>{`
        @keyframes scanMove { 0% { top: 0; opacity: 0; } 15% { opacity: 1; } 85% { opacity: 1; } 100% { top: 100%; opacity: 0; } }
        @keyframes shutterFlash { 0% { opacity: 0; } 10% { opacity: 1; } 100% { opacity: 0; } }
        @keyframes shrinkToTopLeft { 0% { top: 50%; left: 50%; transform: translate(-50%,-50%) scale(1); width: 288px; height: 176px; opacity: 1; } 100% { top: 80px; left: 24px; transform: translate(0,0) scale(1); width: 96px; height: 64px; opacity: 1; } }
        @keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
        .animate-scanning-line   { animation: scanMove       2s infinite linear; }
        .animate-shutter         { animation: shutterFlash   0.5s ease-out forwards; }
        .animate-shrink-captured { animation: shrinkToTopLeft 0.8s cubic-bezier(0.34,1.56,0.64,1) forwards; }
        .animate-fade-in         { animation: fadeIn         0.4s ease-out forwards; }
      `}</style>

      {/* ── Header ── */}
      {view === "select" ? (
        <header className="bg-white border-b border-[#e2e8f0] sticky top-0 z-10 shrink-0">
          <div className="w-full h-[60px] flex items-center px-5">
            <div className="flex items-center gap-3">
              <button onClick={() => router.push("/")} className="w-6 h-6 flex items-center justify-center">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                  <path d="M15 18l-6-6 6-6" stroke="#64748B" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </button>
              <span className="text-[16px] font-semibold text-[#64748b] tracking-[-0.4px]">{tx.refPhotoSetupHeader}</span>
            </div>
          </div>
        </header>
      ) : (
        <header className="px-5 h-16 flex items-center gap-3 border-b border-white/10 shrink-0">
          <button
            onClick={() => {
              if (authPhase === "face-verify" && isPhotoAuth) {
                cancelAnimationFrame(rafRef.current);
                stopCamera();
                scanStatusRef.current = "idle"; setScanStatus("idle"); setScanProgress(0);
                scanStartRef.current = null; setReferenceImage(null);
                setAuthPhase("id-scan"); setCameraFacing("back"); startCamera("back");
              } else {
                router.push(mode === "1:1" ? "/face-auth" : "/");
              }
            }}
            className="w-10 h-10 flex items-center justify-center rounded-xl bg-white/10 active:scale-90 transition-all"
          >
            <ChevronLeft className="w-6 h-6 text-white/70" />
          </button>
          <span className="text-white font-semibold text-[16px] tracking-[-0.4px]">
            {authPhase === "id-scan" ? tx.refPhotoCaptureHeader : mode === "1n" ? tx.matchingNHeader : mode === "liveness" ? tx.livenessHeader : tx.cameraVerHeader}
          </span>
          {userName && <span className="text-white/40 text-[13px] tracking-[-0.3px]">{userName}</span>}
          {mode !== "liveness" && authPhase === "face-verify" && (
            <div className={`ml-auto flex items-center gap-1.5 rounded-full px-2.5 py-1 ${livenessEnabled === true ? "bg-[#0fb981]/20" : "bg-white/10"}`}>
              <div className={`w-1.5 h-1.5 rounded-full ${livenessEnabled === true ? "bg-[#0fb981] animate-pulse" : "bg-white/30"}`} />
              <span className={`text-[11px] font-semibold ${livenessEnabled === true ? "text-[#0fb981]" : "text-white/40"}`}>
                {livenessEnabled === true ? tx.livenessOn : livenessEnabled === false ? tx.livenessOff : tx.liveness}
              </span>
            </div>
          )}
        </header>
      )}

      {/* ── Select view ── */}
      {view === "select" && (
        <SelectView
          tx={tx}
          fileInputRef={fileInputRef}
          onCamera={() => { setView("auth"); setAuthPhase("id-scan"); }}
          onFileChange={(e) => {
            const file = e.target.files?.[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = (ev) => {
              const originalUrl = ev.target?.result as string;
              const img = new window.Image();
              img.onload = () => {
                const canvas = document.createElement("canvas");
                const cropSize = Math.min(img.width, img.height);
                const cx = (img.width - cropSize) / 2;
                const cy = (img.height - cropSize) / 2;
                canvas.width = 640; canvas.height = 640;
                canvas.getContext("2d")?.drawImage(img, cx, cy, cropSize, cropSize, 0, 0, 640, 640);
                const dataUrl = canvas.toDataURL("image/jpeg", 0.85);
                sessionStorage.setItem("faceAuthReferenceImage", dataUrl);
                sessionStorage.setItem("faceAuthRefPhotoSource", "gallery");
                setReferenceImage(dataUrl);
                setView("auth"); setAuthPhase("face-verify");
              };
              img.src = originalUrl;
            };
            reader.readAsDataURL(file);
          }}
        />
      )}

      {/* ── Auth view ── */}
      {view === "auth" && (
        <div className="flex-1 flex flex-col bg-[#020617] overflow-hidden relative animate-fade-in">

          {/* Step badge (id-scan only) */}
          {authPhase === "id-scan" && (
            <div className="absolute top-6 left-0 right-0 z-50 px-6 flex justify-between items-center pointer-events-none">
              <div className="bg-white/10 backdrop-blur-md px-5 py-3 rounded-2xl border border-white/10 flex items-center gap-3">
                <div className="w-2 h-2 rounded-full bg-blue-400 animate-pulse" />
                <p className="text-white text-xs font-bold tracking-tight">
                  {`STEP 1: ${tx.refPhotoSection} (${cameraFacing === "back" ? tx.cameraRear : tx.cameraFront})`}
                </p>
              </div>
              <button
                onClick={() => setIdScanType((p) => p === "id" ? "face" : "id")}
                className="w-12 h-12 bg-white/10 backdrop-blur-md rounded-2xl border border-white/10 flex items-center justify-center pointer-events-auto active:scale-90 transition-all"
              >
                <SwitchCamera className="w-6 h-6 text-white" />
              </button>
            </div>
          )}

          {/* Shutter flash */}
          {isFlashing && <div className="absolute inset-0 bg-white z-[100] animate-shutter pointer-events-none" />}

          <div className={`flex-1 flex flex-col items-center ${authPhase === "id-scan" ? "justify-start pt-24" : referenceImage ? "justify-center" : "justify-start pt-12"} relative`}>

            {/* Reference image thumbnail */}
            {authPhase === "face-verify" && referenceImage && (
              <div className="absolute top-4 left-6 z-40 animate-fade-in">
                <div className="w-24 h-[68px] rounded-2xl overflow-hidden border-2 border-teal-400 shadow-2xl relative">
                  <img src={referenceImage} alt="기준 사진" className="w-full h-full object-cover" />
                  <div className="absolute bottom-0 left-0 right-0 bg-black/60 py-0.5 flex items-center justify-center">
                    <span className="text-[7px] text-white font-bold uppercase tracking-widest">{tx.refPhotoLabel}</span>
                  </div>
                </div>
              </div>
            )}

            {/* Captured thumbnail animation */}
            {authPhase === "id-captured" && (
              <div className="absolute animate-shrink-captured z-40">
                <div className="bg-slate-800 rounded-2xl overflow-hidden border-2 border-teal-400 shadow-[0_0_30px_rgba(45,212,191,0.5)] w-full h-full" />
              </div>
            )}

            {/* ID Scan */}
            {authPhase === "id-scan" && (
              <IdScanSection
                idScanType={idScanType}
                idVideoRef={idVideoRef}
                videoRef={videoRef}
                tx={tx}
                lang={lang}
                onSetIdScanType={setIdScanType}
                onCaptureId={captureIdPhoto}
                onCaptureFace={captureFaceAsReference}
              />
            )}

            {/* Face Verify */}
            {authPhase === "face-verify" && (
              <FaceVerifySection
                referenceImage={referenceImage}
                scanStatus={scanStatus}
                scanProgress={scanProgress}
                isLoading={isLoading}
                apiError={apiError}
                cameraError={cameraError ? (tx[cameraError as keyof typeof tx] as string | undefined ?? cameraError) : null}
                modelReady={modelReady}
                notFrontal={notFrontal}
                faceClipped={faceClipped}
                faceTooFar={faceTooFar}
                multipleFaces={multipleFaces}
                captureMode={captureMode}
                captureModeRef={captureModeRef}
                videoRef={videoRef}
                tx={tx}
                lang={lang}
                onSetCaptureMode={setCaptureMode}
                onManualCapture={manualCapture}
              />
            )}
          </div>
        </div>
      )}

      <ApiErrorSheet
        error={apiError}
        retryLabel={tx.retryScan}
        tx={tx}
        onRetry={retryFromError}
        onHome={() => router.push("/")}
        onLogout={logout}
      />
    </div>
  );
}

export default function FaceAuthCameraPage() {
  return (
    <Suspense>
      <CameraView />
    </Suspense>
  );
}

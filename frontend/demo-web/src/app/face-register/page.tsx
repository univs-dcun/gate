"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useLanguage } from "@/contexts/LanguageContext";
import { useApiKey } from "@/contexts/ApiKeyContext";
import { translations } from "@/lib/translations";
import { registerFace, identifyFace, ApiError, cacheLivenessEnabled } from "@/lib/api";
import { FaceScannerViewport } from "./_components/FaceScannerViewport";
import { RegisterInfoForm } from "./_components/RegisterInfoForm";
import { ApiErrorSheet } from "@/components/ApiErrorSheet";

type InputMethod = "camera" | "file";
type ScanStatus = "idle" | "scanning" | "complete";

const SCAN_HOLD_MS = 2000;

export default function FaceRegisterPage() {
  const router = useRouter();
  const { lang } = useLanguage();
  const tx = translations[lang];
  const { logout } = useApiKey();

  const [inputMethod, setInputMethod] = useState<InputMethod>("camera");
  const [scanStatus, setScanStatus] = useState<ScanStatus>("idle");
  const [scanProgress, setScanProgress] = useState(0);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [capturedImage, setCapturedImage] = useState<string | null>(null);
  const [modelReady, setModelReady] = useState(false);
  const [userName, setUserName] = useState("");
  const [memo, setMemo] = useState("");
  const [notFrontal, setNotFrontal] = useState(false);
  const [faceClipped, setFaceClipped] = useState(false);
  const [faceTooFar, setFaceTooFar] = useState(false);
  const [multipleFaces, setMultipleFaces] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [apiError, setApiError] = useState<ApiError["type"] | null>(null);
  const [uploadNoFace, setUploadNoFace] = useState(false);

  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const fileUploadRef = useRef<HTMLInputElement>(null);
  const rafRef = useRef<number>(0);
  const scanStatusRef = useRef<ScanStatus>("idle");
  const scanStartRef = useRef<number | null>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const faceDetectorRef = useRef<any>(null);
  const lastBbRef = useRef<{ originX: number; originY: number; width: number; height: number } | null>(null);

  // ── MediaPipe init ──
  useEffect(() => {
    let closed = false;
    const init = async () => {
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
    };
    init();
    return () => { closed = true; faceDetectorRef.current?.close(); };
  }, []);

  // ── Camera ──
  const startCamera = async () => {
    setCameraError(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 720 } },
      });
      streamRef.current = stream;
      if (videoRef.current) videoRef.current.srcObject = stream;
    } catch (err) {
      const e = err as DOMException;
      if (e.name === "NotAllowedError" || e.name === "PermissionDeniedError") {
        setCameraError("cameraPermissionDenied");
      } else if (e.name === "NotFoundError") {
        setCameraError("cameraNotFound");
      } else {
        setCameraError("cameraUnavailable");
      }
    }
  };

  const stopCamera = () => {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
  };

  // ── Capture frame ──
  const captureFrame = useCallback(() => {
    const video = videoRef.current;
    if (!video || video.readyState < 2) return;
    const vw = video.videoWidth;
    const vh = video.videoHeight;
    const bb = lastBbRef.current;
    let sx: number, sy: number, sSize: number;
    if (bb && bb.width > 0) {
      const pad = 0.35;
      sSize = Math.min(Math.max(bb.width * (1 + 2 * pad), bb.height * (1 + 2 * pad)), vw, vh);
      sx = Math.max(0, Math.min(bb.originX + bb.width / 2 - sSize / 2, vw - sSize));
      sy = Math.max(0, Math.min(bb.originY + bb.height / 2 - sSize / 2, vh - sSize));
    } else {
      sSize = Math.min(vw, vh);
      sx = (vw - sSize) / 2;
      sy = (vh - sSize) / 2;
    }
    const canvas = document.createElement("canvas");
    canvas.width = 640; canvas.height = 640;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    ctx.translate(640, 0); ctx.scale(-1, 1);
    ctx.drawImage(video, sx, sy, sSize, sSize, 0, 0, 640, 640);
    setCapturedImage(canvas.toDataURL("image/jpeg", 0.92));
  }, []);

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
        const inFrame = bb
          ? bb.originX / vw > margin && bb.originY / vh > margin &&
            (bb.originX + bb.width) / vw < 1 - margin && (bb.originY + bb.height) / vh < 1 - margin
          : true;
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

        const resetScan = () => {
          if (scanStatusRef.current === "scanning") {
            scanStartRef.current = null; scanStatusRef.current = "idle";
            setScanStatus("idle"); setScanProgress(0);
          }
        };

        if (isTooFar) {
          setFaceTooFar(true); setFaceClipped(false); setNotFrontal(false); resetScan();
        } else if (!inFrame) {
          setFaceTooFar(false); setFaceClipped(true); setNotFrontal(false); resetScan();
        } else if (!frontal) {
          setFaceTooFar(false); setFaceClipped(false); setNotFrontal(true); resetScan();
        } else {
          setFaceTooFar(false); setFaceClipped(false); setNotFrontal(false);
          if (scanStartRef.current === null) {
            scanStartRef.current = now;
            scanStatusRef.current = "scanning";
            setScanStatus("scanning");
          }
          const elapsed = now - scanStartRef.current;
          setScanProgress(Math.min((elapsed / SCAN_HOLD_MS) * 100, 100));
          if (elapsed >= SCAN_HOLD_MS) {
            lastBbRef.current = bb ?? null;
            captureFrame();
            scanStatusRef.current = "complete";
            setScanStatus("complete");
            return;
          }
        }
      } else {
        setMultipleFaces(false); setFaceTooFar(false); setNotFrontal(false); setFaceClipped(false);
        if (scanStatusRef.current === "scanning") {
          scanStartRef.current = null; scanStatusRef.current = "idle";
          setScanStatus("idle"); setScanProgress(0);
        }
      }
    } catch { /* ignore */ }
    rafRef.current = requestAnimationFrame(runDetection);
  }, [captureFrame]);

  useEffect(() => {
    if (inputMethod === "camera") {
      scanStatusRef.current = "idle"; scanStartRef.current = null;
      setScanStatus("idle"); setScanProgress(0); setCapturedImage(null);
      startCamera();
    } else {
      scanStatusRef.current = "idle"; setScanStatus("idle"); setScanProgress(0);
      setCapturedImage(null); cancelAnimationFrame(rafRef.current); stopCamera();
    }
    return () => { cancelAnimationFrame(rafRef.current); stopCamera(); };
  }, [inputMethod]);

  useEffect(() => {
    if (inputMethod !== "camera" || !modelReady || cameraError) return;
    rafRef.current = requestAnimationFrame(runDetection);
    return () => cancelAnimationFrame(rafRef.current);
  }, [inputMethod, modelReady, cameraError, runDetection]);

  const handleUploadFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (ev) => {
      const dataUrl = ev.target?.result as string;
      if (!dataUrl) return;
      setUploadNoFace(false);
      const img = new Image();
      img.onload = async () => {
        const detector = faceDetectorRef.current;
        if (detector) {
          try {
            await detector.setOptions({ runningMode: "IMAGE" });
            const result = detector.detect(img);
            await detector.setOptions({ runningMode: "VIDEO" });
            if (!result.detections || result.detections.length === 0) {
              setUploadNoFace(true); setCapturedImage(dataUrl);
              scanStatusRef.current = "idle"; setScanStatus("idle"); return;
            }
          } catch {
            await detector.setOptions({ runningMode: "VIDEO" }).catch(() => {});
            setUploadNoFace(true); setCapturedImage(dataUrl);
            scanStatusRef.current = "idle"; setScanStatus("idle"); return;
          }
        }
        setCapturedImage(dataUrl);
        scanStatusRef.current = "complete"; setScanStatus("complete");
        sessionStorage.setItem("faceRegisterPhoto", dataUrl);
      };
      img.src = dataUrl;
    };
    reader.readAsDataURL(file);
    e.target.value = "";
  };

  const resetScan = useCallback(() => {
    setCapturedImage(null); setScanProgress(0); setNotFrontal(false);
    scanStartRef.current = null; scanStatusRef.current = "idle"; setScanStatus("idle");
    cancelAnimationFrame(rafRef.current);
    rafRef.current = requestAnimationFrame(runDetection);
  }, [runDetection]);

  const handleSubmit = async () => {
    if (!capturedImage) return;
    sessionStorage.setItem("faceRegisterPhoto", capturedImage);
    setIsLoading(true); setApiError(null);
    const name = userName.trim();
    const t0 = Date.now();
    try {
      const result = await registerFace(capturedImage, name, memo.trim() || undefined);
      sessionStorage.setItem("univsRegisterResult", JSON.stringify(result));
      sessionStorage.setItem("univsRegisterLatency", String(Date.now() - t0));
      if (typeof result?.data?.checkLiveness === "boolean") cacheLivenessEnabled(result.data.checkLiveness);
      const encoded = encodeURIComponent(name);
      if (result?.success) {
        router.push(`/face-register/result?status=success&name=${encoded}`);
      } else {
        const errObj = Array.isArray(result?.errors) ? result.errors[0] : result?.errors;
        const code = (errObj?.code ?? "").toLowerCase();
        const type = (errObj?.type ?? "").toLowerCase();
        const msg = (errObj?.message ?? "").toLowerCase();
        if (code.includes("duplicate") || code.includes("already") || type.includes("already") || type.includes("duplicate") || msg.includes("already") || msg.includes("duplicate") || msg.includes("이미 등록")) {
          try {
            const existingName = result?.data?.userDescription ?? "";
            if (existingName) {
              sessionStorage.setItem("univsDuplicateMatchedName", existingName);
            } else {
              const identified = await identifyFace(capturedImage);
              const matchedName = identified?.data?.userDescription ?? "";
              if (matchedName) sessionStorage.setItem("univsDuplicateMatchedName", matchedName);
              else sessionStorage.removeItem("univsDuplicateMatchedName");
            }
          } catch { sessionStorage.removeItem("univsDuplicateMatchedName"); }
          router.push(`/face-register/result?status=duplicate&name=${encoded}`);
        } else {
          router.push(`/face-register/result?status=fail&name=${encoded}`);
        }
      }
    } catch (err) {
      if (err instanceof ApiError && err.type === "unauthorized") logout();
      else setApiError(err instanceof ApiError ? err.type : "unknown");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div
      className="min-h-screen bg-slate-50 flex flex-col text-slate-800"
      style={{ fontFamily: "'Pretendard', -apple-system, BlinkMacSystemFont, system-ui, sans-serif" }}
    >
      {/* Header */}
      <header className="px-4 h-16 flex items-center justify-between sticky top-0 z-40 bg-white/90 backdrop-blur-md border-b border-slate-100">
        <button
          onClick={() => router.push("/")}
          className="w-10 h-10 bg-slate-100 rounded-xl flex items-center justify-center active:scale-90 transition-all"
        >
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#475569" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>
        <h2 className="font-semibold text-[16px] tracking-[-0.4px]">{tx.newFaceRegHeader}</h2>
      </header>

      {/* Guide banner */}
      <div
        className="border-b px-5 py-3 flex gap-3 items-center transition-colors"
        style={{
          backgroundColor: inputMethod === "file" ? (uploadNoFace ? "#fffbeb" : "#eff6ff") : (faceClipped || notFrontal || faceTooFar || multipleFaces) ? "#fffbeb" : "#eff6ff",
          borderColor: inputMethod === "file" ? (uploadNoFace ? "#fde68a" : "#bfdbfe") : (faceClipped || notFrontal || faceTooFar || multipleFaces) ? "#fde68a" : "#bfdbfe",
        }}
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="shrink-0"
          stroke={inputMethod === "file" ? (uploadNoFace ? "#d97706" : "#2563eb") : (faceClipped || notFrontal || faceTooFar || multipleFaces) ? "#d97706" : "#2563eb"}>
          <circle cx="12" cy="12" r="10" /><path d="M12 16v-4" /><path d="M12 8h.01" />
        </svg>
        <p className="text-[11px] leading-normal font-medium"
          style={{ color: inputMethod === "file" ? (uploadNoFace ? "#92400e" : "#1e40af") : (faceClipped || notFrontal || faceTooFar || multipleFaces) ? "#92400e" : "#1e40af" }}>
          {inputMethod === "file" ? (uploadNoFace ? tx.uploadNoFace : tx.uploadGuide) : multipleFaces ? tx.multipleFacesSub : faceTooFar ? tx.faceTooFarSub : faceClipped ? tx.faceClippedSub : notFrontal ? tx.lookStraight : tx.scanAutoComplete}
        </p>
      </div>

      <main className="flex-1 p-4 flex flex-col gap-6 pb-24 max-w-md mx-auto w-full">
        <FaceScannerViewport
          inputMethod={inputMethod}
          capturedImage={capturedImage}
          videoRef={videoRef}
          scanStatus={scanStatus}
          scanProgress={scanProgress}
          cameraError={cameraError ? (tx[cameraError as keyof typeof tx] as string | undefined ?? cameraError) : null}
          modelReady={modelReady}
          notFrontal={notFrontal}
          faceClipped={faceClipped}
          faceTooFar={faceTooFar}
          multipleFaces={multipleFaces}
          uploadNoFace={uploadNoFace}
          tx={tx}
          onReset={resetScan}
          onUploadClick={() => fileUploadRef.current?.click()}
          onMethodChange={(m) => { setInputMethod(m); setUploadNoFace(false); }}
        />
        <RegisterInfoForm
          userName={userName}
          memo={memo}
          scanStatus={scanStatus}
          isLoading={isLoading}
          tx={tx}
          onUserNameChange={setUserName}
          onMemoChange={setMemo}
          onSubmit={handleSubmit}
        />
      </main>

      <input ref={fileUploadRef} type="file" accept="image/*" className="hidden" onChange={handleUploadFile} />

      <ApiErrorSheet
        error={apiError}
        retryLabel={tx.retryRegister}
        tx={tx}
        onRetry={() => setApiError(null)}
        onHome={() => router.push("/")}
        onLogout={logout}
      />
    </div>
  );
}

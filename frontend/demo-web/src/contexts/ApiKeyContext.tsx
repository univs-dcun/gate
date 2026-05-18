"use client";
import { createContext, useContext, useState, useEffect, useCallback } from "react";
import { useRouter, usePathname } from "next/navigation";
import { fetchProjectInfo, getStoredLivenessEnabled } from "@/lib/api";

interface ApiKeyContextType {
  apiKey: string;
  projectName: string;
  projectError: { code: string; message: string } | null;
  livenessEnabled: boolean | null;
  setApiKey: (key: string) => void;
  logout: () => void;
}

const ApiKeyContext = createContext<ApiKeyContextType>({
  apiKey: "",
  projectName: "",
  projectError: null,
  livenessEnabled: null,
  setApiKey: () => {},
  logout: () => {},
});

// Paths where auth result data lives in sessionStorage — redirecting mid-flow would lose the data
const RESULT_PATHS = [
  "/face-auth/result",
  "/face-register/result",
  "/vein/auth/result",
  "/vein/register/result",
];

export function ApiKeyProvider({ children }: { children: React.ReactNode }) {
  const [apiKey, setApiKeyState] = useState("");
  const [projectName, setProjectName] = useState("");
  const [projectError, setProjectError] = useState<{ code: string; message: string } | null>(null);
  const [livenessEnabled, setLivenessEnabled] = useState<boolean | null>(null);
  const [hydrated, setHydrated] = useState(false);
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    const saved = localStorage.getItem("univs_api_key") ?? "";
    setApiKeyState(saved);
    setProjectName(localStorage.getItem("univs_project_name") ?? "");
    setLivenessEnabled(getStoredLivenessEnabled());
    setHydrated(true);

    if (saved) fetchProjectInfo().then((result) => {
      if (result.ok) {
        setProjectError(null);
        if (result.livenessEnabled !== null) setLivenessEnabled(result.livenessEnabled);
      } else {
        setProjectError({ code: result.code, message: result.message });
      }
    });

    const nameHandler = (e: Event) => {
      setProjectName((e as CustomEvent<string>).detail);
      setProjectError(null);
    };
    const livenessHandler = (e: Event) => {
      setLivenessEnabled((e as CustomEvent<boolean>).detail);
    };
    window.addEventListener("univs_project_name_updated", nameHandler);
    window.addEventListener("univs_liveness_updated", livenessHandler);
    return () => {
      window.removeEventListener("univs_project_name_updated", nameHandler);
      window.removeEventListener("univs_liveness_updated", livenessHandler);
    };
  }, []);

  useEffect(() => {
    if (!hydrated) return;

    const storedKey = localStorage.getItem("univs_api_key") ?? "";
    const isLoginPage = pathname === "/login";
    const isResultPage = RESULT_PATHS.some((p) => pathname.startsWith(p));

    if (!storedKey && !isLoginPage && !isResultPage) {
      router.replace("/login");
    }
  }, [hydrated, pathname, router]);

  const setApiKey = useCallback((key: string) => {
    setApiKeyState(key);
    localStorage.setItem("univs_api_key", key);
    fetchProjectInfo().then((result) => {
      if (result.ok) {
        setProjectError(null);
      } else {
        setProjectError({ code: result.code, message: result.message });
      }
    });
  }, []);

  const logout = useCallback(() => {
    setApiKeyState("");
    setProjectName("");
    setProjectError(null);
    localStorage.removeItem("univs_api_key");
    router.replace("/login");
  }, [router]);

  if (!hydrated) return null;

  return (
    <ApiKeyContext.Provider value={{ apiKey, projectName, projectError, livenessEnabled, setApiKey, logout }}>
      {children}
    </ApiKeyContext.Provider>
  );
}

export const useApiKey = () => useContext(ApiKeyContext);

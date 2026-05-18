"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { fetchRegisteredUsers, RegisteredUser } from "@/lib/api";
import { useLanguage } from "@/contexts/LanguageContext";
import { translations } from "@/lib/translations";

function SearchIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
      <circle cx="7" cy="7" r="5.25" stroke="#94A3B8" strokeWidth="1.3"/>
      <path d="M10.75 10.75L13.25 13.25" stroke="#94A3B8" strokeWidth="1.3" strokeLinecap="round"/>
    </svg>
  );
}

function UserCheckIcon() {
  return (
    <svg width="14" height="12" viewBox="0 0 14 12" fill="none">
      <path d="M9 11V9.8C9 9.22 8.78 8.67 8.39 8.27C8 7.87 7.47 7.65 6.9 7.65H2.6C2.03 7.65 1.5 7.87 1.11 8.27C0.72 8.67 0.5 9.22 0.5 9.8V11" stroke="#94A3B8" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round"/>
      <circle cx="4.75" cy="4" r="2.75" stroke="#94A3B8" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round"/>
      <path d="M9 4.5L10.25 5.75L13 3" stroke="#94A3B8" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  );
}

function CircleCheck() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="9.25" stroke="#CBD5E1" strokeWidth="1.5"/>
      <path d="M8 12l2.5 2.5 5.5-5.5" stroke="#CBD5E1" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  );
}

function CircleCheckFilled() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="10" fill="#006FFF"/>
      <path d="M8 12l2.5 2.5 5.5-5.5" stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  );
}

export default function FaceAuthPage() {
  const router = useRouter();
  const { lang } = useLanguage();
  const tx = translations[lang];
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [users, setUsers] = useState<RegisteredUser[]>([]);
  const [usersLoading, setUsersLoading] = useState(true);

  useEffect(() => {
    fetchRegisteredUsers().then((result) => {
      setUsers(result);
      setUsersLoading(false);
    });
  }, []);

  const filteredUsers = users.filter(
    (u) =>
      (u.username ?? "").includes(searchQuery) ||
      (u.userDescription ?? "").includes(searchQuery) ||
      (u.userId ?? "").toLowerCase().includes(searchQuery.toLowerCase())
  );

  const isSearching = searchQuery.trim().length > 0;
  const hasNoResults = isSearching && filteredUsers.length === 0;

  return (
    <div
      className="min-h-screen bg-[#f7f9fb] flex flex-col"
      style={{ fontFamily: "'Pretendard', -apple-system, BlinkMacSystemFont, system-ui, sans-serif" }}
    >
      {/* ── Header ── */}
      <header className="sticky top-0 z-10 bg-white border-b border-[#e2e8f0]">
        <div className="w-full flex items-center gap-3 px-5 h-[60px]">
          <button
            onClick={() => router.push("/")}
            className="w-6 h-6 flex items-center justify-center"
          >
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
              <path d="M15 18l-6-6 6-6" stroke="#64748B" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </button>
          <span className="text-[16px] font-semibold text-[#64748b] tracking-[-0.4px]">{tx.selectUserHeader}</span>
        </div>
      </header>

      {/* ── Body ── */}
      <main className="w-full flex-1 px-5 pt-6 pb-[100px] flex flex-col gap-6">

        {/* Search */}
        <div
          className="bg-white rounded-[12px] px-4 py-3 flex items-center gap-2"
          style={{ border: isSearching ? "1px solid #006FFF" : "1px solid #cbd5e1" }}
        >
          <SearchIcon />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value);
              setSelectedId(null);
            }}
            placeholder={tx.searchPlaceholder}
            className="flex-1 text-[13px] font-semibold text-[#334155] tracking-[-0.325px] outline-none bg-transparent placeholder:text-[#94a3b8] placeholder:font-semibold"
          />
          {isSearching && (
            <button onClick={() => { setSearchQuery(""); setSelectedId(null); }}>
              <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
                <circle cx="11" cy="11" r="9" fill="#CBD5E1"/>
                <path d="M8 8l6 6M14 8l-6 6" stroke="white" strokeWidth="1.5" strokeLinecap="round"/>
              </svg>
            </button>
          )}
        </div>

        {/* Loading / empty / no results */}
        {usersLoading ? (
          <div className="flex-1 flex items-center justify-center pt-20">
            <div className="w-6 h-6 rounded-full border-2 border-[#006FFF] border-t-transparent animate-spin" />
          </div>
        ) : !isSearching && users.length === 0 ? (
          <div className="flex-1 flex flex-col items-center justify-center gap-4 pt-20">
            <div className="flex flex-col items-center gap-1 text-center">
              <p className="text-[14px] font-semibold text-[#64748b] tracking-[-0.35px]">{tx.registeredUsers} 0</p>
              <p className="text-[12px] text-[#94a3b8] tracking-[-0.3px]">{tx.noSearchResultsSub}</p>
            </div>
            <button
              onClick={() => router.push("/face-register")}
              className="flex items-center gap-1 text-[13px] font-semibold text-[#006FFF] tracking-[-0.325px]"
            >
              <span>{tx.goToRegister}</span>
              <svg width="7" height="12" viewBox="0 0 7 12" fill="none">
                <path d="M1 1l5 5-5 5" stroke="#006FFF" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </button>
          </div>
        ) : hasNoResults ? (
          <div className="flex-1 flex flex-col items-center justify-center gap-4 pt-20">
            <svg width="48" height="56" viewBox="0 0 48 56" fill="none">
              <path
                fillRule="evenodd"
                clipRule="evenodd"
                d="M4 2C2.895 2 2 2.895 2 4V52C2 53.105 2.895 54 4 54H44C45.105 54 46 53.105 46 52V16L32 2H4Z"
                fill="white"
                stroke="#CBD5E1"
                strokeWidth="1.5"
              />
              <path
                fillRule="evenodd"
                clipRule="evenodd"
                d="M32 2L46 16H34C32.895 16 32 15.105 32 14V2Z"
                fill="#CBD5E1"
              />
              <path d="M12 26h24M12 33h18" stroke="#CBD5E1" strokeWidth="1.5" strokeLinecap="round"/>
            </svg>
            <div className="flex flex-col items-center gap-1 text-center">
              <p className="text-[14px] font-semibold text-[#64748b] tracking-[-0.35px]">
                {tx.noSearchResults}
              </p>
              <p className="text-[12px] text-[#94a3b8] tracking-[-0.3px]">
                {tx.noSearchResultsSub}
              </p>
            </div>
            <button
              onClick={() => router.push("/face-register")}
              className="flex items-center gap-1 text-[13px] font-semibold text-[#006FFF] tracking-[-0.325px]"
            >
              <span>{tx.goToRegister}</span>
              <svg width="7" height="12" viewBox="0 0 7 12" fill="none">
                <path d="M1 1l5 5-5 5" stroke="#006FFF" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </button>
          </div>
        ) : (
          /* User list */
          <div className="flex flex-col gap-2">
            {!isSearching && (
              <div className="flex items-center gap-1 px-1">
                <div className="flex items-center justify-center px-1 py-0.5">
                  <UserCheckIcon />
                </div>
                <span className="text-[14px] font-semibold text-[#64748b] tracking-[-0.35px]">
                  {tx.registeredUsers}
                </span>
              </div>
            )}

            <div className="flex flex-col gap-3">
              {filteredUsers.map((user) => {
                const isSelected = selectedId === user.userId;
                return (
                  <button
                    key={user.userId}
                    onClick={() => setSelectedId(user.userId)}
                    className="w-full text-left"
                  >
                    <div
                      className="rounded-[12px] px-4 py-3"
                      style={{
                        backgroundColor: isSelected ? "#EFF9FF" : "#FFFFFF",
                        border: isSelected ? "1px solid #006FFF" : "1px solid #E2E8F0",
                      }}
                    >
                      <div className="flex items-center gap-2">
                        <div className="flex-1 flex flex-col gap-1">
                          <p className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">
                            {user.username}
                          </p>
                          <p className="text-[12px] font-medium text-[#94a3b8] tracking-[-0.3px] leading-[1.4]">
                            {user.userDescription}
                          </p>
                        </div>
                        {isSelected ? <CircleCheckFilled /> : <CircleCheck />}
                      </div>
                    </div>
                  </button>
                );
              })}
            </div>
          </div>
        )}

      </main>

      {/* ── Bottom Button ── */}
      <div className="fixed bottom-0 bg-[#f7f9fb] pb-6 pt-3 px-5" style={{ left: "50%", transform: "translateX(-50%)", width: "min(500px, 100vw)", boxSizing: "border-box" }}>
        <div>
          <button
            disabled={!selectedId}
            onClick={() => {
              const user = users.find((u) => u.userId === selectedId);
              router.push(`/face-auth/camera?name=${encodeURIComponent(user?.username ?? user?.userDescription ?? "")}&faceId=${encodeURIComponent(user?.faceId ?? "")}`);
            }}
            className="w-full py-4 rounded-[14px] text-[16px] font-semibold tracking-[-0.4px] transition-colors active:scale-[0.97]"
            style={{
              backgroundColor: selectedId ? "#006FFF" : "#E2E8F0",
              color: selectedId ? "#FFFFFF" : "#94A3B8",
            }}
          >
            {tx.authenticateBtn}
          </button>
        </div>
      </div>
    </div>
  );
}

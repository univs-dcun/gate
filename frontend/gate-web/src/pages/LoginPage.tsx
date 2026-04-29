import { useState } from 'react';
import styles from './LoginPage.module.css';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);

  const isFormValid = email.trim() !== '' && password.trim() !== '';

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={styles.logo}>
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
            <path d="M10 1L18.66 6V14L10 19L1.34 14V6L10 1Z" fill="#006FFF" />
            <path d="M10 4L16.5 7.75V15.25L10 19L3.5 15.25V7.75L10 4Z" fill="#EFF9FF" />
            <path d="M10 6.5L14.5 9.125V14.375L10 17L5.5 14.375V9.125L10 6.5Z" fill="#006FFF" />
          </svg>
          <span className={styles.logoText}>UNIVS GATE</span>
        </div>
        <button className={styles.langButton} type="button">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#475569" strokeWidth="1.5">
            <circle cx="12" cy="12" r="10" />
            <path d="M2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
          </svg>
          <span className={styles.langText}>EN</span>
        </button>
      </header>

      <main className={styles.main}>
        <div className={styles.card}>

          {/* Title */}
          <div className={styles.titleBlock}>
            <h1 className={styles.title}>로그인</h1>
            <p className={styles.subtitle}>UNIVS GATE에 오신 것을 환영합니다.</p>
          </div>

          {/* Input Fields */}
          <div className={styles.inputFields}>
            <div className={styles.inputGroup}>

              {/* Email */}
              <div className={styles.field}>
                <label className={styles.label}>이메일</label>
                <div className={styles.inputWrapper}>
                  <span className={styles.inputIcon}>
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#1E293B" strokeWidth="1.5">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                      <circle cx="12" cy="7" r="4" />
                    </svg>
                  </span>
                  <input
                    className={styles.input}
                    type="email"
                    placeholder="user@email.com"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                  />
                </div>
              </div>

              {/* Password */}
              <div className={styles.field}>
                <label className={styles.label}>비밀번호</label>
                <div className={styles.inputWrapper}>
                  <span className={styles.inputIcon}>
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#1E293B" strokeWidth="1.5">
                      <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                    </svg>
                  </span>
                  <input
                    className={styles.input}
                    type={showPassword ? 'text' : 'password'}
                    placeholder="비밀번호를 입력해주세요"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                  />
                  <button
                    className={styles.visibilityButton}
                    type="button"
                    onClick={() => setShowPassword((prev) => !prev)}
                    aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
                  >
                    {showPassword ? (
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#1E293B" strokeWidth="1.5">
                        <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
                        <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
                        <line x1="1" y1="1" x2="23" y2="23" />
                      </svg>
                    ) : (
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#1E293B" strokeWidth="1.5">
                        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                        <circle cx="12" cy="12" r="3" />
                      </svg>
                    )}
                  </button>
                </div>
              </div>

            </div>

            {/* Options + Button */}
            <div className={styles.bottomBlock}>
              <div className={styles.options}>
                <label className={styles.checkboxLabel}>
                  <input
                    type="checkbox"
                    checked={rememberMe}
                    onChange={(e) => setRememberMe(e.target.checked)}
                    className={styles.checkbox}
                  />
                  로그인 상태 유지
                </label>
                <button className={styles.forgotButton} type="button">
                  비밀번호 찾기
                </button>
              </div>

              <button
                className={`${styles.loginButton} ${isFormValid ? styles.loginButtonActive : ''}`}
                type="button"
                disabled={!isFormValid}
              >
                로그인
              </button>
            </div>
          </div>

          {/* Signup */}
          <p className={styles.signupText}>
            계정이 없으신가요?{' '}
            <button className={styles.signupLink} type="button">
              계정 만들기
            </button>
          </p>

        </div>
      </main>
    </div>
  );
}

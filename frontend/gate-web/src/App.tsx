import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { ProjectProvider, useProjectContext } from '@/contexts/ProjectContext';
import HomePage from '@/pages/HomePage';
import LoginPage from '@/pages/LoginPage';
import DashboardPage from '@/pages/DashboardPage';
import LogDetailPage from '@/pages/LogDetailPage';
import FeaturesPage from '@/pages/FeaturesPage';
import FeaturesConsentPage from '@/pages/FeaturesConsentPage';
import LogDetailConsentPage from '@/pages/LogDetailConsentPage';
import MobileTestPage from '@/pages/MobileTestPage';
import ProjectWelcomePage from '@/pages/ProjectWelcomePage';
import ProjectListPage from '@/pages/ProjectListPage';
import SignupPage from '@/pages/SignupPage';
import VerifyEmailPage from '@/pages/VerifyEmailPage';
import SetPasswordPage from '@/pages/SetPasswordPage';
import ForgotPasswordPage from '@/pages/ForgotPasswordPage';
import DevSupportPage from '@/pages/DevSupportPage';
import SettingsPage from '@/pages/SettingsPage';
import NetworkErrorPage from '@/pages/NetworkErrorPage';
import { getProjectSettings } from '@/services/project';
import MobileNetworkErrorPage from '@/pages/MobileNetworkErrorPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // 5분
      retry: 1,
    },
  },
});

function PrivateRoute({ element }: { element: React.ReactElement }) {
  return localStorage.getItem('access_token') ? element : <Navigate to="/login" replace />;
}

function LogsRoute() {
  const { selectedProject } = useProjectContext();
  const { data: settings } = useQuery({
    queryKey:  ['project-settings', selectedProject?.id],
    queryFn:   () => getProjectSettings(Number(selectedProject!.id)).then(r => r.data.data),
    enabled:   !!selectedProject?.id,
    staleTime: 0,
  });
  return settings?.consentEnabled ? <LogDetailConsentPage /> : <LogDetailPage />;
}

function FeaturesRoute() {
  const { selectedProject } = useProjectContext();
  const { data: settings } = useQuery({
    queryKey:  ['project-settings', selectedProject?.id],
    queryFn:   () => getProjectSettings(Number(selectedProject!.id)).then(r => r.data.data),
    enabled:   !!selectedProject?.id,
    staleTime: 0,
  });
  return settings?.consentEnabled ? <FeaturesConsentPage /> : <FeaturesPage />;
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <ProjectProvider>
        <Routes>
          {/* 인증 불필요 */}
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/verify-email" element={<VerifyEmailPage />} />
          <Route path="/set-password" element={<SetPasswordPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/m/test/:type" element={<MobileTestPage />} />
          <Route path="/m/network-error" element={<MobileNetworkErrorPage />} />
          <Route path="/network-error" element={<NetworkErrorPage />} />

          {/* 인증 필요 */}
          <Route path="/dashboard" element={<PrivateRoute element={<DashboardPage />} />} />
          <Route path="/dashboard/logs" element={<PrivateRoute element={<LogsRoute />} />} />
          <Route path="/dashboard/features" element={<PrivateRoute element={<FeaturesRoute />} />} />
          <Route path="/dashboard/support" element={<PrivateRoute element={<DevSupportPage />} />} />
          <Route path="/dashboard/settings" element={<PrivateRoute element={<SettingsPage />} />} />
          <Route path="/welcome" element={<PrivateRoute element={<ProjectWelcomePage />} />} />
          <Route path="/projects" element={<PrivateRoute element={<ProjectListPage />} />} />
        </Routes>
        </ProjectProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

export default App;

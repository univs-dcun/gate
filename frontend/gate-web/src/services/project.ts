import httpClient from './http';

/* ── 타입 ──────────────────────────────────────────────── */
export interface Project {
  projectId:              number;
  projectName:            string;
  projectDescription:     string;
  projectType?:           'STANDARD' | 'EXTERNAL';
  projectModuleType?:     'FACE' | 'PALM';
  packageKey?:            string;
  countUserRegistration?: number;
  countVerifyById?:       number;   // 1:1 촬영인증
  countVerifyByImage?:    number;   // 1:1 사진인증
  countIdentify?:         number;
  countLiveness?:         number;
  status:                 'ACTIVE' | 'INACTIVE' | 'DELETED';
  apiKey:                 string | null;
  createdAt:              string;
  updatedAt:              string;
}

export interface ProjectsData {
  contents: Project[];
  page: {
    totalElements: number;
    totalPages:    number;
    page:          number;
    pageSize:      number;
    totalCount?:   number;
  };
}

interface ApiResponse<T> {
  success: boolean;
  data: T;
  errors: Record<string, string> | null;
}

/* ── API 함수 ───────────────────────────────────────────── */

/** 프로젝트 목록 조회 */
export const getProjects = (params?: { projectKeyword?: string; page?: number; pageSize?: number }) =>
  httpClient.get<ApiResponse<ProjectsData>>('/v1/projects', { params });

export interface CreateProjectRequest {
  projectName:        string;
  projectDescription: string;
  projectType:        'STANDARD' | 'EXTERNAL';
  projectModuleType:  'FACE' | 'PALM';
}

/** 프로젝트 생성 */
export const createProject = (body: CreateProjectRequest) =>
  httpClient.post<ApiResponse<Project>>('/v1/projects', body);

/** 패키지 키 설정 (External 타입 전용) */
export const updatePackageKey = (projectId: number, packageKey: string) =>
  httpClient.patch<ApiResponse<Project>>(`/v1/projects/${projectId}/package-key`, { packageKey });

export interface UpdateProjectRequest {
  projectName:        string;
  projectDescription: string;
}

/** 프로젝트 정보 수정 */
export const updateProject = (projectId: number, body: UpdateProjectRequest) =>
  httpClient.put<ApiResponse<Project>>(`/v1/projects/${projectId}`, body);

/* ── 프로젝트 설정 ── */
export type LivenessModuleType = 'FACE' | 'PALM';
export type LivenessOperation  = 'REGISTER' | 'IDENTIFY' | 'VERIFY_ID' | 'VERIFY_IMAGE';

/** 모듈(얼굴/손바닥)·동작(등록/매칭/검증)별 라이브니스 사용 여부 */
export interface LivenessSetting {
  moduleType: LivenessModuleType;
  operation:  LivenessOperation;
  enabled:    boolean;
}

export interface ProjectSettings {
  projectSettingsId: number;
  projectId:         number;
  projectName?:      string;
  projectType?:      'STANDARD' | 'EXTERNAL';
  packageKey?:       string;
  consentEnabled:    boolean;
  consentAgreedAt:   string | null;
  livenessSettings:  LivenessSetting[];  // 모듈별 라이브니스 설정 목록
}

/** livenessSettings 배열에서 특정 modality+operation 의 enabled 조회 */
export const isLivenessEnabled = (
  settings: ProjectSettings | undefined,
  moduleType: LivenessModuleType,
  operation: LivenessOperation,
): boolean =>
  settings?.livenessSettings?.some(
    (s) => s.moduleType === moduleType && s.operation === operation && s.enabled,
  ) ?? false;

/** 프로젝트 설정 조회 */
export const getProjectSettings = (projectId: number) =>
  httpClient.get<ApiResponse<ProjectSettings>>(`/v1/projects/${projectId}/settings`);

export interface OperationSetting {
  operation: LivenessOperation;
  enabled:   boolean;
}

/** 라이브니스 사용 여부 설정 (modality 별) */
export const updateLivenessSettings = (
  projectId: number,
  moduleType: LivenessModuleType,
  settings: OperationSetting[],
) =>
  httpClient.put<ApiResponse<ProjectSettings>>(`/v1/projects/${projectId}/settings/liveness`, {
    moduleType,
    settings,
  });

/** 모바일 데모 활성화 설정 */
export const updateDemoMode = (projectId: number, demoEnabled: boolean) =>
  httpClient.put<ApiResponse<ProjectSettings>>(`/v1/projects/${projectId}/settings/demo`, { demoEnabled });

/** SDK(토큰) 모드 활성화 설정 */
export const updateSdkMode = (projectId: number, sdkEnabled: boolean) =>
  httpClient.put<ApiResponse<ProjectSettings>>(`/v1/projects/${projectId}/settings/sdk`, { sdkEnabled });

/** 개인정보 노출 동의 설정 */
export const updateConsentSettings = (projectId: number, consentEnabled: boolean) =>
  httpClient.put<ApiResponse<ProjectSettings>>(`/v1/projects/${projectId}/settings/consent`, { consentEnabled });

/** 개인정보 동의 변경 이력 */
export interface ConsentLog {
  id:                  number;
  projectId:           number;
  endUserIdentifier:   number;
  consentType:         string;
  agreed:              boolean;
  ipAddress:           string;
  agreedAt:            string;
  createdAt:           string;
}

export const getConsentLogs = (projectId: number) =>
  httpClient.get<ApiResponse<{ contents: ConsentLog[] }>>(`/v1/projects/${projectId}/settings/consent/logs`);

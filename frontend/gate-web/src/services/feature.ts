import httpClient from './http';

/* ──────────────────────────────────────────────────────────
 * 특징점(Feature) 서비스 — 신규 백엔드 /api/v1/feature 통합 API
 *  - 목록: GET /v1/feature  (featureType 으로 얼굴/손바닥 필터)
 *  - 등록: POST /v1/feature/face | /v1/feature/palm  (multipart)
 *  - 삭제: DELETE /v1/feature/{face|palm}/{featureId}
 * ────────────────────────────────────────────────────────── */

export type FeatureType = 'FACE' | 'PALM';

/** 목록 행 (통합 응답을 화면 표시용으로 정규화) */
export interface FeatureRow {
  featureId:       number;        // DB 식별자(face/palmFeatureId) — 일련번호 · 삭제 키
  featureType:     FeatureType;   // 인증 방식
  faceId:          string;        // FID (표시용)
  userDescription: string | null; // 메모
  faceImagePath:   string;        // 특징 이미지 URL
  createdAt:       string;
}

export interface FeaturesData {
  content: FeatureRow[];
  page: {
    totalElements: number;
    totalPages:    number;
    page:          number;
    pageSize:      number;
    totalCount?:   number;
  };
}

interface ApiResponse<T> { success: boolean; data: T; errors: Record<string, string> | null }

interface FeatureItemResponse {
  featureType: FeatureType;
  featureId:   number;
  description: string | null;
  imageUrl:    string;
  fid:         string;
  createdAt:   string;
}
interface FeatureListResponse {
  features: FeatureItemResponse[];
  page:     FeaturesData['page'];
}

export interface GetFeaturesParams {
  featureType?: FeatureType;   // 미지정 = 전체(얼굴+손바닥)
  keyword?:     string;
  page?:        number;
  pageSize?:    number;
  isDeleted?:   boolean;
  startDate?:   string;
  endDate?:     string;
}

/** 특징점 목록 조회 (얼굴+손바닥 통합) */
export const getFeatures = async (params?: GetFeaturesParams): Promise<FeaturesData> => {
  const res = await httpClient.get<ApiResponse<FeatureListResponse>>('/v1/feature', {
    params,
    skipNetworkErrorRedirect: true,
  });
  const d = res.data.data;
  return {
    content: (d.features ?? []).map((f) => ({
      featureId:       f.featureId,
      featureType:     f.featureType,
      faceId:          f.fid,
      userDescription: f.description,
      faceImagePath:   f.imageUrl,
      createdAt:       f.createdAt,
    })),
    page: d.page,
  };
};

const featurePath = (type: FeatureType) => (type === 'PALM' ? 'palm' : 'face');

/** 얼굴 특징점 수동 등록 */
export const registerFaceFeature = (
  featureImage: File,
  opts?: { description?: string; username?: string; transactionUuid?: string },
) => {
  const fd = new FormData();
  fd.append('featureImage', featureImage);
  if (opts?.description !== undefined) fd.append('description', opts.description);
  if (opts?.username)                  fd.append('username', opts.username);
  if (opts?.transactionUuid)           fd.append('transactionUuid', opts.transactionUuid);
  return httpClient.post<ApiResponse<unknown>>('/v1/feature/face', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    skipNetworkErrorRedirect: true,
  });
};

/** 손바닥 특징점 수동 등록 */
export const registerPalmFeature = (
  featureImage: File,
  opts?: { description?: string; username?: string; transactionUuid?: string },
) => {
  const fd = new FormData();
  fd.append('featureImage', featureImage);
  if (opts?.description !== undefined) fd.append('description', opts.description);
  if (opts?.username)                  fd.append('username', opts.username);
  if (opts?.transactionUuid)           fd.append('transactionUuid', opts.transactionUuid);
  return httpClient.post<ApiResponse<unknown>>('/v1/feature/palm', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    skipNetworkErrorRedirect: true,
  });
};

/** 특징점 정보 수정 (modality 에 따라 face/palm 경로 분기) */
export const updateFeature = (
  featureType: FeatureType,
  featureId: number,
  opts: { featureImage?: File; description?: string; username?: string; transactionUuid?: string },
) => {
  const fd = new FormData();
  if (opts.featureImage)              fd.append('featureImage', opts.featureImage);
  if (opts.description !== undefined) fd.append('description', opts.description);
  if (opts.username)                  fd.append('username', opts.username);
  if (opts.transactionUuid)           fd.append('transactionUuid', opts.transactionUuid);
  return httpClient.put<ApiResponse<unknown>>(`/v1/feature/${featurePath(featureType)}/${featureId}`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    skipNetworkErrorRedirect: true,
  });
};

/** 특징점 삭제 (modality 에 따라 face/palm 경로 분기) */
export const deleteFeature = (featureType: FeatureType, featureId: number) =>
  httpClient.delete<ApiResponse<null>>(`/v1/feature/${featurePath(featureType)}/${featureId}`, {
    skipNetworkErrorRedirect: true,
  });

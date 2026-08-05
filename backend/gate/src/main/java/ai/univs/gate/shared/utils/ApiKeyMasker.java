package ai.univs.gate.shared.utils;

import org.springframework.util.StringUtils;

/**
 * 로그에 남길 API 키를 가리는 유틸.
 *
 * <p>API 키는 특징점 등록·매칭 전 기능의 인증 수단이다. 원문을 로그에 남기면 로그 열람 권한만으로
 * 남의 생체 API 를 호출할 수 있고, 온프레미스 납품 시 로그 묶음이 그대로 밖으로 나간다. 요청 추적에는
 * 앞자리만 있어도 충분하다. (UG-274 반박 리뷰)
 *
 * <p>UG-281 에서 소유 검증 실패 로그가 두 번째 사용처가 되면서, 같은 규칙이 두 곳에 복사되지 않도록
 * 여기로 뺐다. 사용자에게 보여주는 마스킹({@code ApiKeyResult.maskApiKey})은 목적도 형식도 달라
 * 합치지 않았다 — 그쪽은 "내 키가 맞는지 눈으로 확인" 이 목적이라 뒷자리를 남긴다.
 */
public final class ApiKeyMasker {

    private ApiKeyMasker() {
    }

    public static String mask(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return apiKey;
        }
        return apiKey.length() <= 8 ? "****" : apiKey.substring(0, 8) + "****";
    }
}

package ai.univs.gate.support.file;

import org.springframework.util.StringUtils;

/**
 * 한 프로젝트의 응답에 실을 사진 URL 을 만든다 (UG-386).
 *
 * <p>예전에는 {@code (prefixImagePath, consentEnabled)} 두 값을 결과 객체마다 넘겨 {@code prefix + path} 로 이어 붙였다.
 * 이제 URL 마다 프로젝트에 묶인 서명이 붙어야 하므로, 그 두 값 대신 이 객체 하나를 넘긴다.
 *
 * <p>동의가 꺼져 있거나 경로가 비어 있으면 빈 문자열이다 — 응답 계약(빈 값)은 그대로다.
 */
public record ImageUrls(String baseUrl, Signer signer, long projectId, boolean consentEnabled) {

    /** 파일 API 쿼리 문자열을 만든다 — 운영은 {@link FileUrlSigner}, 테스트는 서명 없는 가짜를 넣는다. */
    @FunctionalInterface
    public interface Signer {
        String query(String filePath, long projectId);
    }

    public String of(String imagePath) {
        if (!consentEnabled || !StringUtils.hasText(imagePath)) {
            return "";
        }
        return baseUrl + "?" + signer.query(imagePath, projectId);
    }
}

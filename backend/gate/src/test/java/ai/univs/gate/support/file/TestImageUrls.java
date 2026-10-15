package ai.univs.gate.support.file;

import org.mockito.stubbing.Answer;

/**
 * 결과 조립만 보는 테스트용 {@link ImageUrls} (UG-386) — 서명 없이 {@code 접두어 + 경로} 를 만든다.
 * 서명 자체는 {@code FileUrlSignerTest} · {@code FileAccessGuardTest} 가 본다.
 */
public final class TestImageUrls {

    private TestImageUrls() {
    }

    /** {@code fileService.imageUrls(projectId, consent)} 의 답 — 예전 {@code getFileServerPath()} 접두어("…?filePath=")를 그대로 쓴다. */
    public static Answer<ImageUrls> prefix(String fileServerPath) {
        String marker = "?filePath=";
        String base = fileServerPath.substring(0, fileServerPath.indexOf(marker));
        return invocation -> new ImageUrls(base, (path, projectId) -> "filePath=" + path,
                invocation.getArgument(0), invocation.getArgument(1));
    }
}

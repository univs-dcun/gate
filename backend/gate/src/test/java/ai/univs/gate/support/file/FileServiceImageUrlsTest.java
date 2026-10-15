package ai.univs.gate.support.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("FileService.imageUrls — 응답에 실을 사진 URL (UG-386)")
class FileServiceImageUrlsTest {

    @Test
    @DisplayName("게이트웨이 주소 + 파일 API 경로에, 그 프로젝트로 서명한 쿼리를 붙인다. 동의가 꺼져 있으면 빈 값")
    void imageUrls() {
        FileUrlSigner signer = new FileUrlSigner("k".repeat(FileUrlSigner.MIN_SECRET_LENGTH), "", 600,
                Clock.fixed(Instant.ofEpochSecond(1_000), ZoneOffset.UTC));
        FileService service = new FileService(mock(FileUtil.class), signer);
        ReflectionTestUtils.setField(service, "GATEWAY_URL", "https://gw");
        ReflectionTestUtils.setField(service, "FILE_GET_API_ENDPOINT", "/api/v1/file");

        assertThat(service.imageUrls(7L, true).of("/face/a.jpg"))
                .startsWith("https://gw/api/v1/file?filePath=%2Fface%2Fa.jpg&pid=7&exp=1600&sig=");
        assertThat(service.imageUrls(7L, false).of("/face/a.jpg")).isEmpty();
    }
}

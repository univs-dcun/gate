package ai.univs.gate.support.file;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.support.file.FileUtil.DeleteOutcome;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("UG-347: 파일 파기 결과 — 이미 없음과 저장소를 볼 수 없음을 나눈다")
class FileUtilDeleteReportingTest {

    @TempDir Path root;

    private FileUtil fileUtil() {
        FileUtil util = new FileUtil();
        ReflectionTestUtils.setField(util, "fileRootPath", root.toString());
        return util;
    }

    @Test
    @DisplayName("있으면 지우고 DELETED")
    void 지움() throws Exception {
        Files.createDirectories(root.resolve("face/20261006"));
        Files.writeString(root.resolve("face/20261006/a.jpg"), "x");

        assertThat(fileUtil().deleteReporting("/face/20261006/a.jpg")).isEqualTo(DeleteOutcome.DELETED);
        assertThat(root.resolve("face/20261006/a.jpg")).doesNotExist();
    }

    @Test
    @DisplayName("파일만 없고 폴더는 있으면 ALREADY_GONE")
    void 이미_없음() throws Exception {
        Files.createDirectories(root.resolve("face/20261006"));

        assertThat(fileUtil().deleteReporting("/face/20261006/b.jpg")).isEqualTo(DeleteOutcome.ALREADY_GONE);
    }

    @Test
    @DisplayName("폴더조차 없으면 STORAGE_UNAVAILABLE — 볼륨이 빠졌거나 루트가 어긋났을 수 있다")
    void 저장소_없음() throws Exception {
        assertThat(fileUtil().deleteReporting("/face/20250101/c.jpg")).isEqualTo(DeleteOutcome.STORAGE_UNAVAILABLE);
    }
}

package ai.univs.gate.support.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.support.file.FileUtil.DeleteOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.api.Test;

@DisplayName("UG-347: FileService 삭제 — 빈 경로는 저장소 루트를 건드리지 않고 거절한다")
class FileServiceDeleteTest {

    private final FileUtil fileUtil = mock(FileUtil.class);
    private final FileService fileService = new FileService(fileUtil);

    @Test
    @DisplayName("deleteReporting 은 FileUtil 의 결과를 그대로 돌려준다")
    void 결과_전달() {
        given(fileUtil.deleteReporting("/face/a.jpg")).willReturn(DeleteOutcome.ALREADY_GONE);

        assertThat(fileService.deleteReporting("/face/a.jpg")).isEqualTo(DeleteOutcome.ALREADY_GONE);
    }

    @Test
    @DisplayName("delete 는 FileUtil 에 위임한다")
    void 위임() {
        fileService.delete("/face/b.jpg");

        verify(fileUtil).delete("/face/b.jpg");
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @DisplayName("빈 경로는 거절한다 — 루트에 빈 문자열을 붙이면 저장소 루트 자체를 가리킨다")
    void 빈_경로(String path) {
        assertThatThrownBy(() -> fileService.deleteReporting(path)).isInstanceOf(CustomGateException.class);
        assertThatThrownBy(() -> fileService.delete(path)).isInstanceOf(CustomGateException.class);
        // any() — anyString() 은 null 과 맞지 않아 null 케이스에서 아무것도 검증하지 못한다
        verify(fileUtil, never()).deleteReporting(any());
        verify(fileUtil, never()).delete(any());
    }
}

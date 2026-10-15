package ai.univs.gate.support.file;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.repository.ProjectRepository;
import ai.univs.gate.modules.project.domain.repository.ProjectSettingsRepository;
import ai.univs.gate.shared.exception.FileAccessDeniedException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("사진 API 접근 검사 (UG-386)")
class FileAccessGuardTest {

    private static final String PATH = "/face/20261015/abc.jpg";
    private static final long PID = 7L;

    private final FileUrlSigner signer = mock(FileUrlSigner.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectSettingsRepository settingsRepository = mock(ProjectSettingsRepository.class);
    private final Project project = mock(Project.class);

    @BeforeEach
    void setUp() {
        given(signer.verify(PATH, PID, 100L, "good")).willReturn(true);
        given(projectRepository.findByIdAndIsDeletedFalse(PID)).willReturn(Optional.of(project));
    }

    private FileAccessGuard guard(boolean required) {
        return new FileAccessGuard(signer, projectRepository, settingsRepository, required);
    }

    private void consent(boolean enabled) {
        given(settingsRepository.findByProject(project)).willReturn(Optional.of(
                ProjectSettings.builder().id(1L).project(project).consentEnabled(enabled).build()));
    }

    @Test
    @DisplayName("서명 · 동의가 맞으면 통과")
    void 통과() {
        consent(true);
        assertThatCode(() -> guard(true).check(PATH, PID, 100L, "good")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("서명 없는 예전 URL — 필수면 거절, 유예 중이면 통과")
    void 서명_없음() {
        assertThatThrownBy(() -> guard(true).check(PATH, null, null, null)).isInstanceOf(FileAccessDeniedException.class);
        assertThatCode(() -> guard(false).check(PATH, null, null, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("서명이 붙어 있는데 틀렸거나 일부만 있으면 유예와 상관없이 거절")
    void 틀린_서명() {
        consent(true);
        for (boolean required : new boolean[] {true, false}) {
            assertThatThrownBy(() -> guard(required).check(PATH, PID, 100L, "bad")).isInstanceOf(FileAccessDeniedException.class);
            assertThatThrownBy(() -> guard(required).check(PATH, PID, null, "good")).isInstanceOf(FileAccessDeniedException.class);
            assertThatThrownBy(() -> guard(required).check(PATH, null, 100L, "good")).isInstanceOf(FileAccessDeniedException.class);
            assertThatThrownBy(() -> guard(required).check(PATH, PID, 100L, null)).isInstanceOf(FileAccessDeniedException.class);
        }
    }

    @Test
    @DisplayName("서명이 맞아도 그 프로젝트의 동의가 지금 꺼져 있으면 거절 — 동의를 끈 뒤 남은 URL")
    void 동의_꺼짐() {
        consent(false);
        assertThatThrownBy(() -> guard(true).check(PATH, PID, 100L, "good")).isInstanceOf(FileAccessDeniedException.class);
    }

    @Test
    @DisplayName("삭제된 프로젝트 · 설정 없음은 거절")
    void 삭제된_프로젝트() {
        given(projectRepository.findByIdAndIsDeletedFalse(PID)).willReturn(Optional.empty());
        assertThatThrownBy(() -> guard(true).check(PATH, PID, 100L, "good")).isInstanceOf(FileAccessDeniedException.class);

        given(projectRepository.findByIdAndIsDeletedFalse(PID)).willReturn(Optional.of(project));
        given(settingsRepository.findByProject(project)).willReturn(Optional.empty());
        assertThatThrownBy(() -> guard(true).check(PATH, PID, 100L, "good")).isInstanceOf(FileAccessDeniedException.class);
    }

    @Test
    @DisplayName("동의는 프로젝트마다 잠깐 기억한다 — 목록 한 쪽의 사진 수만큼 DB 를 읽지 않게")
    void 동의_캐시() {
        consent(true);
        FileAccessGuard guard = guard(true);
        for (int i = 0; i < 20; i++) {
            guard.check(PATH, PID, 100L, "good");
        }
        verify(projectRepository, times(1)).findByIdAndIsDeletedFalse(PID);
    }

    @Test
    @DisplayName("「꺼짐」은 기억하지 않는다 — 동의를 켠 직후 받은 URL 이 바로 열린다")
    void 꺼짐은_캐시하지_않음() {
        FileAccessGuard guard = guard(true);
        consent(false);
        assertThatThrownBy(() -> guard.check(PATH, PID, 100L, "good")).isInstanceOf(FileAccessDeniedException.class);

        consent(true);
        guard.check(PATH, PID, 100L, "good");
    }
}

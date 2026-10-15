package ai.univs.gate.shared.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.univs.gate.shared.exception.FileAccessDeniedException;
import ai.univs.gate.shared.exception.GlobalExceptionHandler;
import ai.univs.gate.support.file.FileAccessGuard;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.message.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("사진 API 응답 (UG-386)")
class FileControllerResponseTest {

    private static final String PATH = "/face/20261015/abc.jpg";

    private final FileService fileService = mock(FileService.class);
    private final FileAccessGuard guard = mock(FileAccessGuard.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        MessageService messageService = mock(MessageService.class);
        given(messageService.getMessage(anyString())).willReturn("msg");
        mvc = MockMvcBuilders.standaloneSetup(new FileController(fileService, guard))
                .setControllerAdvice(new GlobalExceptionHandler(messageService))
                .build();
    }

    @Test
    @DisplayName("내려줄 때 캐시 금지 · nosniff 헤더를 붙이고, 받은 서명 값을 검사에 넘긴다")
    void 정상_헤더() throws Exception {
        given(fileService.down(PATH)).willReturn(new byte[] {1, 2, 3});

        mvc.perform(get("/api/v1/file").param("filePath", PATH).param("pid", "7").param("exp", "100").param("sig", "s"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        verify(guard).check(PATH, 7L, 100L, "s");
    }

    @Test
    @DisplayName("검사에서 거절되면 403 · FILE-106 이고 파일을 읽지 않는다")
    void 거절() throws Exception {
        willThrow(new FileAccessDeniedException()).given(guard).check(any(), any(), any(), any());

        mvc.perform(get("/api/v1/file").param("filePath", PATH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors.code").value("FILE-106"));
        verify(fileService, never()).down(anyString());
    }
}

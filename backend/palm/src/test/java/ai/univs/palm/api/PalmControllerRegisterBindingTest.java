package ai.univs.palm.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.univs.palm.application.input.RegisterInput;
import ai.univs.palm.application.result.RegisterResult;
import ai.univs.palm.application.usecase.DeleteUseCase;
import ai.univs.palm.application.usecase.IdentifyUseCase;
import ai.univs.palm.application.usecase.LivenessUseCase;
import ai.univs.palm.application.usecase.RegisterBranchUseCase;
import ai.univs.palm.application.usecase.RegisterUseCase;
import ai.univs.palm.shared.locale.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 등록 요청의 palmId 가 HTTP 바인딩을 거쳐 유스케이스까지 가는가 (UG-337).
 *
 * <p>DTO 단위 테스트는 레코드 생성자를 직접 부르므로 <b>바인딩 이름</b>을 보지 못한다. 반박 리뷰가 레코드
 * 컴포넌트에 다른 바인딩 이름을 붙이는 변이로, palmId 가 조용히 무시되고 형식 검증까지 사라지는데 기존
 * 테스트가 전부 초록인 것을 보였다. 그러면 서버가 새 id 를 발급하고 gate 가 남긴 id 는 가리키는 것이
 * 없어진다.
 */
@WebMvcTest(
        controllers = PalmController.class,
        properties = {
                "spring.cloud.config.enabled=false",
                "eureka.client.enabled=false",
                // SmartFace 클라이언트가 등록되려면 URL 이 있어야 한다. 이 테스트는 부르지 않는다.
                "palm.module.url=http://localhost:0"
        }
)
@DisplayName("UG-337: 팜 등록 요청의 palmId 바인딩")
class PalmControllerRegisterBindingTest {

    private static final String 발급 = "0f8fad5b-d9cb-469f-a165-70867728950e";

    @Autowired private MockMvc mockMvc;

    @MockBean private RegisterBranchUseCase registerBranchUseCase;
    @MockBean private RegisterUseCase registerUseCase;
    @MockBean private DeleteUseCase deleteUseCase;
    @MockBean private LivenessUseCase livenessUseCase;
    @MockBean private IdentifyUseCase identifyUseCase;
    @MockBean private MessageService messageService;

    private final MockMultipartFile 이미지 =
            new MockMultipartFile("palmImage", "p.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3});

    @BeforeEach
    void setUp() {
        given(messageService.getMessage(any(String.class))).willReturn("에러 메시지");
        given(registerUseCase.execute(any())).willReturn(new RegisterResult("branch-A", 발급, "txn"));
    }

    @Test
    @DisplayName("palmId 파라미터가 유스케이스 입력으로 간다")
    void palmId_가_입력으로_간다() throws Exception {
        mockMvc.perform(multipart("/api/v1/palm").file(이미지)
                        .param("branchName", "branch-A")
                        .param("palmId", 발급))
                .andExpect(status().isOk());

        ArgumentCaptor<RegisterInput> input = ArgumentCaptor.forClass(RegisterInput.class);
        verify(registerUseCase).execute(input.capture());
        assertThat(input.getValue().palmId()).isEqualTo(발급);
    }

    @Test
    @DisplayName("palmId 가 UUID 가 아니면 400 이고 유스케이스를 부르지 않는다")
    void 형식이_틀리면_400() throws Exception {
        mockMvc.perform(multipart("/api/v1/palm").file(이미지)
                        .param("branchName", "branch-A")
                        .param("palmId", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registerUseCase);
    }

    @Test
    @DisplayName("palmId 를 주지 않으면 빈 값으로 간다 — 서버가 발급한다")
    void 없으면_빈값() throws Exception {
        mockMvc.perform(multipart("/api/v1/palm").file(이미지)
                        .param("branchName", "branch-A"))
                .andExpect(status().isOk());

        ArgumentCaptor<RegisterInput> input = ArgumentCaptor.forClass(RegisterInput.class);
        verify(registerUseCase).execute(input.capture());
        assertThat(input.getValue().palmId()).isEmpty();
    }
}

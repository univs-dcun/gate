package ai.univs.gate.modules.feature.api.controller;

import ai.univs.gate.modules.feature.application.input.UpdateFeatureDescriptionInput;
import ai.univs.gate.modules.feature.application.result.face.FaceFeatureResult;
import ai.univs.gate.modules.feature.application.result.palm.PalmFeatureResult;
import ai.univs.gate.modules.feature.application.usecase.face.UpdateFaceFeatureDescriptionUseCase;
import ai.univs.gate.modules.feature.application.usecase.palm.UpdatePalmFeatureDescriptionUseCase;
import ai.univs.gate.shared.auth.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UG-376: 메모 수정 엔드포인트의 배선 — JSON 본문이 검증을 거쳐 유스케이스로 그대로 가는가.
 *
 * <p>{@link ApiKeyPropagationTest} 는 apiKey 만 본다. 여기서는 실제 MVC 바인딩(JSON → {@code @Valid} DTO)으로 경로의 ID·메모가
 * 제자리에 가는지, {@code description} 키가 없으면 400 인지 본다 — 키 오타가 조용히 메모를 지우지 않게 (반박 리뷰 M).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UG-376: 메모 수정 엔드포인트 배선")
class FeatureDescriptionWiringTest {

    private static final long ACCOUNT_ID = 42L;
    private static final String API_KEY = "AK-UG376";

    @Mock private UpdateFaceFeatureDescriptionUseCase updateFaceFeatureDescriptionUseCase;
    @Mock private UpdatePalmFeatureDescriptionUseCase updatePalmFeatureDescriptionUseCase;
    @InjectMocks private FaceController faceController;
    @InjectMocks private PalmController palmController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(faceController, palmController)
                .setValidator(new LocalValidatorFactoryBean() {{
                    afterPropertiesSet();
                }})
                .build();
        UserContext.set(UserContext.builder()
                .accountId(String.valueOf(ACCOUNT_ID)).apiKey(API_KEY).timezone("Asia/Seoul").build());
        LocalDateTime now = LocalDateTime.of(2026, 10, 13, 9, 0);
        given(updateFaceFeatureDescriptionUseCase.execute(any()))
                .willReturn(new FaceFeatureResult(7L, 1L, "fid", "memo", null, now, "tx", null, null));
        given(updatePalmFeatureDescriptionUseCase.execute(any()))
                .willReturn(new PalmFeatureResult(8L, 1L, "pid", "palm memo", null, now, "tx", null, null));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static UpdateFeatureDescriptionInput 입력(org.mockito.ArgumentCaptor<UpdateFeatureDescriptionInput> captor) {
        return captor.getValue();
    }

    @Test
    @DisplayName("얼굴: 경로의 ID·본문 메모·계정·키가 그대로 유스케이스로 가고, 응답은 단건 조회 모양")
    void 얼굴_배선() throws Exception {
        mockMvc.perform(patch("/api/v1/feature/face/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"memo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.faceFeatureId").value(7))
                .andExpect(jsonPath("$.data.description").value("memo"));

        ArgumentCaptor<UpdateFeatureDescriptionInput> captor = ArgumentCaptor.forClass(UpdateFeatureDescriptionInput.class);
        verify(updateFaceFeatureDescriptionUseCase).execute(captor.capture());
        assertThat(입력(captor)).isEqualTo(new UpdateFeatureDescriptionInput(ACCOUNT_ID, API_KEY, 7L, "memo"));
        verify(updatePalmFeatureDescriptionUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("팜: 경로의 ID·본문 메모가 팜 유스케이스로 간다")
    void 팜_배선() throws Exception {
        mockMvc.perform(patch("/api/v1/feature/palm/8").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"palm memo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.palmFeatureId").value(8));

        ArgumentCaptor<UpdateFeatureDescriptionInput> captor = ArgumentCaptor.forClass(UpdateFeatureDescriptionInput.class);
        verify(updatePalmFeatureDescriptionUseCase).execute(captor.capture());
        assertThat(입력(captor)).isEqualTo(new UpdateFeatureDescriptionInput(ACCOUNT_ID, API_KEY, 8L, "palm memo"));
    }

    @Test
    @DisplayName("빈 문자열은 그대로 간다 — 지우기")
    void 빈_문자열() throws Exception {
        mockMvc.perform(patch("/api/v1/feature/face/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateFeatureDescriptionInput> captor = ArgumentCaptor.forClass(UpdateFeatureDescriptionInput.class);
        verify(updateFaceFeatureDescriptionUseCase).execute(captor.capture());
        assertThat(입력(captor).description()).isEmpty();
    }

    @Test
    @DisplayName("description 키가 없으면(오타 포함) 400 REQUIRED_DESCRIPTION — 메모를 지우지 않는다")
    void 키_없음() throws Exception {
        for (String body : new String[]{"{}", "{\"desc\":\"x\"}", "{\"description\":null}"}) {
            var result = mockMvc.perform(patch("/api/v1/feature/face/7").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            assertThat(result.getResolvedException()).isInstanceOf(MethodArgumentNotValidException.class);
            assertThat(((MethodArgumentNotValidException) result.getResolvedException()).getBindingResult().getAllErrors())
                    .extracting(e -> e.getDefaultMessage()).containsExactly("REQUIRED_DESCRIPTION");
        }
        verify(updateFaceFeatureDescriptionUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("1001자는 400 INVALID_DESCRIPTION_LENGTH, 1000자는 통과")
    void 길이_상한() throws Exception {
        var result = mockMvc.perform(patch("/api/v1/feature/palm/8").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + "a".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(((MethodArgumentNotValidException) result.getResolvedException()).getBindingResult().getAllErrors())
                .extracting(e -> e.getDefaultMessage()).containsExactly("INVALID_DESCRIPTION_LENGTH");
        verify(updatePalmFeatureDescriptionUseCase, never()).execute(any());

        mockMvc.perform(patch("/api/v1/feature/palm/8").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + "a".repeat(1000) + "\"}"))
                .andExpect(status().isOk());
    }
}

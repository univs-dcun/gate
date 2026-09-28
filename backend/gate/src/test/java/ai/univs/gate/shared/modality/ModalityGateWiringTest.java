package ai.univs.gate.shared.modality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.univs.gate.shared.auth.UserContextInterceptor;
import ai.univs.gate.shared.exception.GlobalExceptionHandler;
import ai.univs.gate.shared.locale.LocaleConfig;
import ai.univs.gate.shared.web.WebMvcConfig;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.message.MessageService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

/**
 * {@code WebMvcConfig} 가 차단 인터셉터를 <b>실제로</b> 등록하고, 거절이 계약대로 나가는가 (UG-223).
 *
 * <p>단위 테스트와 커버리지 가드는 인터셉터와 패턴을 각각 본다. 둘이 옳아도 {@code WebMvcConfig}
 * 가 등록을 빠뜨리면 아무것도 막히지 않는다. 여기서는 설정이 만든 등록을 그대로 꺼내 MockMvc 에
 * 넣는다 — 설정이 붙인 경로 패턴까지 그 등록 안에 들어 있다.
 *
 * <p>응답은 다른 비즈니스 오류와 같은 계약이다 — HTTP 400, 본문 {@code errors.code}.
 */
@DisplayName("UG-223: 차단 인터셉터 등록과 응답")
class ModalityGateWiringTest {

    @RestController
    static class ProbeController {
        @PostMapping("/api/v1/feature/palm")
        String registerPalm() { return "ok"; }

        @DeleteMapping("/api/v1/feature/palm/{id}")
        String deletePalm() { return "ok"; }

        @GetMapping("/api/v1/feature/palm")
        String listPalm() { return "ok"; }

        @PostMapping("/api/v1/demo/feature/palm/identify")
        String demoIdentifyPalm() { return "ok"; }

        @PostMapping("/api/v1/feature/face")
        String registerFace() { return "ok"; }

        @GetMapping("/api/v1/match")
        String history() { return "ok"; }
    }

    /** 등록 목록을 꺼내기 위한 구멍. {@code getInterceptors} 는 protected 다. */
    private static class 들여다보는_레지스트리 extends InterceptorRegistry {
        List<Object> 등록된_것() {
            return getInterceptors();
        }
    }

    private static List<MappedInterceptor> 차단_등록(ModalityProperties props) {
        var registry = new 들여다보는_레지스트리();
        new WebMvcConfig(mock(UserContextInterceptor.class), new LocaleConfig(), props).addInterceptors(registry);

        return registry.등록된_것().stream()
                .filter(MappedInterceptor.class::isInstance)
                .map(MappedInterceptor.class::cast)
                .filter(m -> m.getInterceptor() instanceof ModalityGateInterceptor)
                .toList();
    }

    private static MockMvc mockMvc(ModalityProperties props) {
        MessageService messageService = mock(MessageService.class);
        given(messageService.getMessage(any(ErrorType.class))).willReturn("꺼져 있다");

        return MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler(messageService))
                .addInterceptors(차단_등록(props).toArray(HandlerInterceptor[]::new))
                .build();
    }

    @Test
    @DisplayName("방식마다 하나씩, 경로 패턴을 붙여 등록한다")
    void 방식마다_등록한다() {
        List<MappedInterceptor> 등록 = 차단_등록(new ModalityProperties(true, true));

        assertThat(등록).hasSize(2);
        assertThat(등록).allSatisfy(m -> assertThat(m.getIncludePathPatterns())
                .as("패턴 없이 등록되면 모든 요청에 걸린다").isNotEmpty());
    }

    @Test
    @DisplayName("palm 을 끄면 palm 동작은 400 + CMMN-104 로 거절된다")
    void 꺼진_동작은_거절된다() throws Exception {
        MockMvc mvc = mockMvc(new ModalityProperties(true, false));

        mvc.perform(post("/api/v1/feature/palm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.code").value("CMMN-104"))
                .andExpect(jsonPath("$.errors.type").value("FEATURE_NOT_ENABLED"));
        mvc.perform(delete("/api/v1/feature/palm/7")).andExpect(jsonPath("$.errors.code").value("CMMN-104"));
        mvc.perform(post("/api/v1/demo/feature/palm/identify")).andExpect(jsonPath("$.errors.code").value("CMMN-104"));
    }

    @Test
    @DisplayName("palm 을 꺼도 palm 조회와 face 동작, 다른 경로는 그대로다")
    void 나머지는_그대로다() throws Exception {
        MockMvc mvc = mockMvc(new ModalityProperties(true, false));

        mvc.perform(get("/api/v1/feature/palm")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/feature/face")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/match")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("기본값(둘 다 켜짐)에서는 아무것도 막지 않는다")
    void 기본값은_막지_않는다() throws Exception {
        MockMvc mvc = mockMvc(new ModalityProperties(true, true));

        mvc.perform(post("/api/v1/feature/palm")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/demo/feature/palm/identify")).andExpect(status().isOk());
    }
}

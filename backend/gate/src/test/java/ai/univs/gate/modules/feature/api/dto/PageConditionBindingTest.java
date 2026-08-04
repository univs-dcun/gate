package ai.univs.gate.modules.feature.api.dto;

import ai.univs.gate.facade.feature.api.controller.FeatureController;
import ai.univs.gate.facade.feature.api.dto.FeatureSelectCondition;
import ai.univs.gate.modules.feature.api.controller.FaceController;
import ai.univs.gate.modules.feature.api.controller.MatchController;
import ai.univs.gate.modules.feature.api.controller.PalmController;
import ai.univs.gate.modules.feature.api.dto.face.FaceFeatureSelectCondition;
import ai.univs.gate.modules.feature.api.dto.match.MatchingHistorySelectCondition;
import ai.univs.gate.modules.feature.api.dto.palm.PalmFeatureSelectCondition;
import jakarta.validation.Valid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UG-268 회귀 방지 — 스프링 MVC 바인딩 계층.
 *
 * <p>{@link PageConditionDefaultsTest} 는 DTO 단위라 배선을 검증하지 못한다. 컨트롤러에서
 * {@code @Valid} 를 지워도 그쪽 테스트는 전부 통과한다. 여기서는 실제 조건 DTO를
 * {@code @ModelAttribute @Valid} 로 받는 컨트롤러를 세워 두 가지를 확인한다.
 *
 * <ol>
 *   <li>쿼리 파라미터를 생략하면 null 로 바인딩된다 — primitive 였을 때 0이 들어가 500이 났다
 *   <li>범위 밖 값은 {@link MethodArgumentNotValidException} 으로 떨어져 400이 된다
 *       — {@code BindException} 이었다면 GlobalExceptionHandler 가 못 잡고 500이 됐을 것이다
 * </ol>
 */
class PageConditionBindingTest {

    private static PalmFeatureSelectCondition capturedPalm;
    private static FeatureSelectCondition capturedFeature;

    @RestController
    static class ProbeController {

        @GetMapping("/probe/palm")
        String palm(@ModelAttribute @Valid PalmFeatureSelectCondition condition) {
            capturedPalm = condition;
            return "ok";
        }

        @GetMapping("/probe/feature")
        String feature(@ModelAttribute @Valid FeatureSelectCondition condition) {
            capturedFeature = condition;
            return "ok";
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ProbeController())
            .setValidator(new LocalValidatorFactoryBean() {{
                afterPropertiesSet();
            }})
            .build();

    @Test
    @DisplayName("팜: 파라미터를 생략하면 null 로 바인딩되고 200 — primitive 였다면 0/0 으로 500")
    void 팜_생략시_null바인딩() throws Exception {
        mockMvc.perform(get("/probe/palm").accept(MediaType.ALL))
                .andExpect(status().isOk());

        assertThat(capturedPalm.page()).isNull();
        assertThat(capturedPalm.pageSize()).isNull();
        assertThat(capturedPalm.toQuery(1L, "api-key").pageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("팜: 범위 밖 값은 400 (MethodArgumentNotValidException)")
    void 팜_범위밖_400() throws Exception {
        var result = mockMvc.perform(get("/probe/palm").param("page", "0").param("pageSize", "5000"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResolvedException())
                .isInstanceOf(MethodArgumentNotValidException.class);
        assertThat(((MethodArgumentNotValidException) result.getResolvedException())
                .getBindingResult().getAllErrors())
                .allMatch(e -> "INVALID_PAGE_COUNT".equals(e.getDefaultMessage()));
    }

    @Test
    @DisplayName("통합 목록: 파라미터를 생략하면 null 로 바인딩되고 200")
    void 통합_생략시_null바인딩() throws Exception {
        mockMvc.perform(get("/probe/feature").accept(MediaType.ALL))
                .andExpect(status().isOk());

        assertThat(capturedFeature.page()).isNull();
        assertThat(capturedFeature.pageSize()).isNull();
        assertThat(capturedFeature.toQuery(1L, "api-key", "Asia/Seoul").pageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("통합 목록: 범위 밖 값은 400")
    void 통합_범위밖_400() throws Exception {
        mockMvc.perform(get("/probe/feature").param("pageSize", "1001"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 위 테스트들은 프로브 컨트롤러를 쓰므로 실제 컨트롤러에서 {@code @Valid} 를 떼어내는
     * 회귀는 잡지 못한다. 제약이 붙은 조건 DTO를 받는 핸들러는 반드시 {@code @Valid} 를
     * 달고 있어야 하므로, 실제 컨트롤러 시그니처를 직접 확인한다.
     */
    @Test
    @DisplayName("목록 조회 핸들러는 조건 DTO 파라미터에 @Valid 를 유지해야 한다")
    void 실제_컨트롤러의_Valid_유지() {
        assertHandlerValidates(FeatureController.class, FeatureSelectCondition.class);
        assertHandlerValidates(PalmController.class, PalmFeatureSelectCondition.class);
        assertHandlerValidates(FaceController.class, FaceFeatureSelectCondition.class);
        assertHandlerValidates(MatchController.class, MatchingHistorySelectCondition.class);
    }

    private void assertHandlerValidates(Class<?> controller, Class<?> conditionType) {
        var handlers = Arrays.stream(controller.getDeclaredMethods())
                .flatMap(m -> Arrays.stream(m.getParameters()))
                .filter(p -> p.getType().equals(conditionType))
                .toList();

        assertThat(handlers)
                .as("%s 에서 %s 를 받는 파라미터", controller.getSimpleName(), conditionType.getSimpleName())
                .isNotEmpty();
        assertThat(handlers)
                .as("%s 의 %s 파라미터에 @Valid 가 없다", controller.getSimpleName(), conditionType.getSimpleName())
                .allMatch(p -> p.isAnnotationPresent(Valid.class));
    }
}

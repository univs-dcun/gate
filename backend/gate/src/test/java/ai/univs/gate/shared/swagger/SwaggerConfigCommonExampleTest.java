package ai.univs.gate.shared.swagger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ai.univs.gate.shared.web.dto.ResponseApi;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.message.MessageService;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;

/**
 * UG-309: {@link SwaggerConfig} 가 공통으로 심는 예시가 제 오류 코드를 달고 있는지 본다.
 *
 * <p>{@code settingExamples} 는 예시 이름을 <b>문자열</b>로 모은 뒤 {@code ErrorType.from(name)}
 * 으로 되찾는다. 그 메서드는 못 찾으면 던지지 않고 조용히 {@code INTERNAL_SERVER_ERROR} 로
 * 떨어뜨린다.
 *
 * <p>공통 다섯 줄이 {@code HttpStatus.XXX.name()} 을 쓰고 있었는데, 넷은 우연히 같은 이름의
 * {@link ErrorType} 이 있어 맞았고 {@code FORBIDDEN} 만 없었다. 그 결과 <b>54개 엔드포인트
 * 전부</b>가 403 버킷에 {@code PJ-005}(INTERNAL_SERVER_ERROR) 짜리 예시를 달고 있었다.
 * 맞는 이름은 {@code NEED_SERVICE_ROLE}(PJ-002)이다.
 *
 * <p><b>왜 {@link SwaggerErrorContractTest} 로 부족한가.</b> 그쪽은 커밋된
 * {@code api-contract/openapi.json} 을 본다. {@code SwaggerConfig} 를 되돌려도 JSON 은 그대로라
 * 알아채지 못한다 — 변이 심기로 확인했다(SURVIVE). 이 테스트가 그 구멍을 메운다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-309: 공통 오류 예시가 제 코드를 단다")
class SwaggerConfigCommonExampleTest {

    @Mock
    private MessageService messageService;

    private SwaggerConfig swaggerConfig;

    @BeforeEach
    void setUp() {
        swaggerConfig = new SwaggerConfig(messageService);
        given(messageService.getMessage(any(ErrorType.class))).willReturn("메시지");
    }

    /** {@code @SwaggerErrorExample} 이 붙은 최소 핸들러. 공통 항목만 보려고 선언을 비워 둔다. */
    @SwaggerErrorExample({})
    public void 더미핸들러() {
        // 리플렉션 대상일 뿐이라 본문이 없다.
    }

    @Test
    @DisplayName("모든 공통 예시가 자기 이름의 ErrorType 코드를 단다 — 조용한 폴백이 없다")
    void 공통_예시가_제_코드를_단다() throws NoSuchMethodException {
        Map<Integer, Map<String, String>> 상태별_코드 = 공통_예시를_뽑는다();

        // 이름 → 코드. from() 이 폴백되면 이름과 코드가 어긋난다.
        Map<String, String> 이름별_코드 = new LinkedHashMap<>();
        상태별_코드.values().forEach(이름별_코드::putAll);

        assertThat(이름별_코드).isNotEmpty();
        이름별_코드.forEach((name, code) ->
                assertThat(code)
                        .as("예시 이름 '%s' 로 ErrorType.from() 이 못 찾아 폴백했다. "
                                + "SwaggerConfig 는 HttpStatus 이름이 아니라 ErrorType 상수를 써야 한다",
                                name)
                        .isEqualTo(ErrorType.valueOf(name).getCode()));
    }

    @Test
    @DisplayName("403 예시는 NEED_SERVICE_ROLE(PJ-002)이다 — 이 티켓의 본문")
    void 삼공삼은_NEED_SERVICE_ROLE() throws NoSuchMethodException {
        Map<String, String> 사공삼 = 공통_예시를_뽑는다().get(403);

        assertThat(사공삼)
                .as("예전에는 이름이 FORBIDDEN 이었고, ErrorType 에 그 이름이 없어 "
                        + "INTERNAL_SERVER_ERROR(PJ-005)로 폴백했다")
                .containsExactly(Map.entry(ErrorType.NEED_SERVICE_ROLE.name(), "PJ-002"));
    }

    /**
     * 실제 {@link SwaggerConfig#customizer()} 를 돌려 상태코드 → (예시 이름 → 오류 코드) 로 편다.
     *
     * <p>규칙을 복제하지 않고 프로덕션 코드를 그대로 실행한다는 점이 이 테스트의 요점이다.
     */
    private Map<Integer, Map<String, String>> 공통_예시를_뽑는다() throws NoSuchMethodException {
        Method handler = getClass().getMethod("더미핸들러");
        Operation operation = new Operation().responses(new ApiResponses());

        swaggerConfig.customizer().customize(operation, new HandlerMethod(this, handler));

        Map<Integer, Map<String, String>> result = new LinkedHashMap<>();
        operation.getResponses().forEach((status, response) -> {
            Map<String, Example> examples =
                    response.getContent().get("application/json").getExamples();
            Map<String, String> codes = new LinkedHashMap<>();
            examples.forEach((name, example) ->
                    codes.put(name, ((ResponseApi<?>) example.getValue()).errors().code()));
            result.put(Integer.parseInt(status), codes);
        });
        return result;
    }
}

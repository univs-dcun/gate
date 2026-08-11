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

    /**
     * 공통 슬롯 <b>전부</b>를 상태코드·이름·오류코드까지 못박는다.
     *
     * <p>초판은 403 하나만 봤다. 반박 리뷰가 401 을 {@code ErrorType.INVALID_TOKEN} 으로
     * 바꿔치기하는 변이를 심었는데 <b>전 테스트가 초록이었다</b> — 54개 엔드포인트의 401
     * 예시가 {@code PJ-001} 에서 {@code AUTH-106} 으로 바뀌는데 아무도 몰랐다.
     * 위 {@link #공통_예시가_제_코드를_단다()} 는 이름↔코드 <b>자기정합성</b>만 보므로
     * {@code INVALID_TOKEN}→{@code AUTH-106} 처럼 정합적인 바꿔치기를 통과시킨다.
     *
     * <p>그래서 여기서 기대값을 통째로 고정한다. 슬롯을 늘리거나 줄이거나 다른
     * {@link ErrorType} 으로 갈아 끼우면 전부 여기서 걸린다.
     */
    @Test
    @DisplayName("공통 슬롯의 상태코드·이름·오류코드가 전부 고정돼 있다")
    void 공통_슬롯_전부_고정() throws NoSuchMethodException {
        assertThat(공통_예시를_뽑는다()).isEqualTo(Map.of(
                401, Map.of(ErrorType.UNAUTHORIZED.name(), "PJ-001"),
                404, Map.of(ErrorType.NOT_FOUND.name(), "PJ-003"),
                405, Map.of(ErrorType.METHOD_NOT_ALLOWED.name(), "PJ-004"),
                500, Map.of(ErrorType.INTERNAL_SERVER_ERROR.name(), "PJ-005")));
    }

    /**
     * 403 자리는 없어야 한다.
     *
     * <p>이 서비스는 403 을 낼 수 없다 — gate 에 Spring Security 의존성이 없어
     * {@code AccessDeniedException} 경로가 없고, {@code GlobalExceptionHandler} 의
     * {@code @ResponseStatus} 는 400·404·405·500 뿐이며, {@code NEED_SERVICE_ROLE} 을 던지는
     * 프로덕션 코드가 0곳이다. 던지더라도 {@code handleBusinessException} 이
     * {@code BAD_REQUEST} 고정이라 400 으로 나간다.
     *
     * <p>UG-309 는 이 자리를 두 번 잘못 다뤘다 — 먼저 {@code FORBIDDEN} 이라는 없는 이름으로
     * {@code PJ-005} 를 심었고(초판), 다음엔 {@code NEED_SERVICE_ROLE} 로 "고쳤다". 둘 다
     * 아무도 볼 수 없는 예시다. 반박 리뷰가 그것을 짚어 자리를 없앴다.
     *
     * <p>다시 넣고 싶어지면 <b>먼저 403 을 실제로 내는 코드</b>를 만들 것. 그러면 이 테스트가
     * 그 사실을 알려 준다.
     */
    @Test
    @DisplayName("403 예시를 심지 않는다 — 이 서비스는 403 을 낼 수 없다")
    void 사공삼은_없다() throws NoSuchMethodException {
        assertThat(공통_예시를_뽑는다()).doesNotContainKey(403);
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

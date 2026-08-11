package ai.univs.gate.support.api_key;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.facade.demo.api.dto.DemoProjectConfigRequestDTO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.validator.constraints.Length;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 발급되는 API 키 길이가 요청 DTO 의 상한을 넘지 않는다 (UG-310 반박 리뷰 F3).
 *
 * <p>UG-310 이 데모 {@code /config} 에 {@code @Valid} 를 붙이면서 죽어 있던
 * {@code @Length(max = 36)} 가 살아났다. 그런데 실제 발급 키가 <b>정확히 36자</b>다 —
 * {@code prefix "gate_"}(5) + {@code length 31}. 여유가 0 이다.
 *
 * <p>그 두 값의 단일 진실은 <b>이 레포가 아니라</b> {@code gate-config} 레포의
 * {@code gate-service.yml} 이고, 그쪽은 PR 없이 main 에 직접 push 하는 곳이다. 즉
 * {@code length: 31 → 32} 든 프리픽스에 한 글자 추가든, 한 글자짜리 변경 하나가 QR 데모
 * 세션이 가장 먼저 두드리는 엔드포인트를 <b>키 조회에 닿기도 전에</b> PJ-101 로 전부
 * 실패시킨다. 반박 리뷰가 실측으로 보여 줬다.
 *
 * <p><b>이 테스트가 덮는 범위는 레포 안의 값까지다.</b> {@code application-openapi.yml} 은
 * 계약 스펙을 뽑으려고 config-server 값을 흉내 낸 사본이라, 그쪽이 먼저 어긋나면 여기서
 * 걸린다. 그러나 gate-config 만 바꾸고 이 사본을 안 바꾸면 이 테스트는 초록이다 — 레포
 * 경계를 넘는 검사는 불가능하다. 그래서 두 값이 gate-config 를 따라가야 한다는 사실 자체를
 * 여기 적어 둔다.
 */
@DisplayName("UG-310: 발급 API 키 길이 ≤ 요청 DTO 상한")
class ApiKeyLengthContractTest {

    /** 계약 스펙 생성용 프로파일. config-server 가 공급하는 값을 그대로 흉내 낸 사본이다. */
    private static final String CONFIG_SAMPLE = "application-openapi.yml";

    @Test
    @DisplayName("실제로 생성한 키가 데모 요청 DTO 의 제약을 통과한다")
    void 생성한_키가_제약을_통과한다() throws IOException {
        String apiKey = 실제로_키를_만든다();

        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        var violations = validator.validate(new DemoProjectConfigRequestDTO(apiKey));

        assertThat(violations)
                .as("발급 키(%d자)가 자기 DTO 의 제약에 걸린다. api-key.prefix·length 를 줄이거나 "
                        + "@Length(max) 를 늘릴 것. 지금 값은 gate-config/gate-service.yml 이 정한다",
                        apiKey.length())
                .isEmpty();
    }

    /**
     * 여유가 0 이라는 사실 자체를 기록한다.
     *
     * <p>위 테스트만 있으면 "지금은 통과하니까 괜찮다" 로 읽힌다. 실제로는 <b>한 글자만
     * 늘어나도</b> 깨지는 상태다. 여유가 생기면 이 테스트가 깨지면서 사람이 그 사실을 보게
     * 되고, 그때 이 주석과 함께 기대값을 갱신하면 된다.
     */
    @Test
    @DisplayName("여유가 정확히 0 이다 — 한 글자만 늘려도 깨진다")
    void 여유가_없다는_것을_기록한다() throws IOException {
        int 발급 = 실제로_키를_만든다().length();
        int 상한 = 상한을_읽는다();

        assertThat(발급)
                .as("여유가 생겼거나 줄었다. 의도한 변경이면 이 단언을 갱신하고, "
                        + "gate-config 의 api-key.prefix·length 와도 맞는지 확인할 것")
                .isEqualTo(상한);
    }

    /** 같은 제약이 붙은 데모 DTO 전부가 같은 상한을 쓴다 — 하나만 고치고 나머지를 잊는 것을 막는다. */
    @Test
    @DisplayName("데모 요청 DTO 들의 apiKey 상한이 서로 같다")
    void 상한이_흩어져_있지_않다() {
        List<String> 어긋남 = new ArrayList<>();
        int 기준 = 상한을_읽는다();

        for (Class<?> dto : 데모_DTO들()) {
            Length length = apiKey_제약(dto);
            if (length == null || length.max() != 기준) {
                어긋남.add("%s.apiKey max=%s".formatted(
                        dto.getSimpleName(), length == null ? "없음" : length.max()));
            }
        }

        assertThat(어긋남).isEmpty();
    }

    private static String 실제로_키를_만든다() throws IOException {
        Binder binder = 설정_사본();
        ApiKeyGenerator generator = new ApiKeyGenerator();
        ReflectionTestUtils.setField(generator, "apiKeyPrefix",
                binder.bind("api-key.prefix", String.class).get());
        ReflectionTestUtils.setField(generator, "apiKeyLength",
                binder.bind("api-key.length", Integer.class).get());
        return generator.generateApiKey();
    }

    private static int 상한을_읽는다() {
        Length length = apiKey_제약(DemoProjectConfigRequestDTO.class);
        assertThat(length).as("DemoProjectConfigRequestDTO.apiKey 에 @Length 가 없다").isNotNull();
        return length.max();
    }

    /**
     * record 의 {@code apiKey} 에 붙은 {@code @Length} 를 읽는다.
     *
     * <p><b>{@code RecordComponent.getAnnotation} 으로는 못 읽는다.</b> 하이버네이트의
     * {@code @Length} 는 {@code @Target} 에 {@code RECORD_COMPONENT} 가 없어서, 컴파일러가
     * 그것을 필드·접근자 쪽으로만 전파한다. 처음에 구성요소에서 읽으려다 NPE 로 확인했다.
     */
    private static Length apiKey_제약(Class<?> dto) {
        try {
            return dto.getDeclaredField("apiKey").getAnnotation(Length.class);
        } catch (NoSuchFieldException e) {
            throw new AssertionError("%s 에 apiKey 필드가 없다".formatted(dto.getSimpleName()), e);
        }
    }

    private static List<Class<?>> 데모_DTO들() {
        return List.of(
                DemoProjectConfigRequestDTO.class,
                ai.univs.gate.facade.demo.api.dto.VerifyByApiKeyRequestDTO.class,
                ai.univs.gate.facade.demo.api.dto.VerifyByImageAndApiKeyRequestDTO.class,
                ai.univs.gate.facade.demo.api.dto.LivenessByApiKeyRequestDTO.class,
                ai.univs.gate.facade.demo.api.dto.DemoIdentifyRequestDTO.class,
                ai.univs.gate.facade.demo.api.dto.GetUsersByApiKeyRequestDTO.class,
                ai.univs.gate.facade.demo.api.dto.CreateFaceFeatureByApiKeyRequestDTO.class,
                ai.univs.gate.facade.demo.api.dto.CreatePalmFeatureByApiKeyRequestDTO.class,
                ai.univs.gate.facade.demo.api.dto.DemoPalmIdentifyRequestDTO.class,
                ai.univs.gate.facade.demo.api.dto.DemoPalmLivenessRequestDTO.class);
    }

    private static Binder 설정_사본() throws IOException {
        List<PropertySource<?>> loaded = new YamlPropertySourceLoader()
                .load(CONFIG_SAMPLE, new ClassPathResource(CONFIG_SAMPLE));
        MutablePropertySources sources = new MutablePropertySources();
        loaded.forEach(sources::addLast);
        if (loaded.isEmpty()) {
            sources.addLast(new MapPropertySource("empty", java.util.Map.of()));
        }
        return new Binder(ConfigurationPropertySources.from(sources));
    }
}

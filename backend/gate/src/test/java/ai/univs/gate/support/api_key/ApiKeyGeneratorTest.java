package ai.univs.gate.support.api_key;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("ApiKeyGenerator 단위 테스트")
class ApiKeyGeneratorTest {

    private static final String API_KEY_PREFIX = "gate_";
    private static final int API_KEY_LENGTH = 31;
    // URL-safe Base64 문자 집합: A-Z a-z 0-9 - _
    private static final String URL_SAFE_PATTERN = "[A-Za-z0-9_-]+";

    private ApiKeyGenerator apiKeyGenerator;

    @BeforeEach
    void setUp() {
        apiKeyGenerator = new ApiKeyGenerator();
        ReflectionTestUtils.setField(apiKeyGenerator, "apiKeyPrefix", API_KEY_PREFIX);
        ReflectionTestUtils.setField(apiKeyGenerator, "apiKeyLength", API_KEY_LENGTH);
    }

    @Test
    @DisplayName("generateApiKey는 prefix로 시작하고 전체 길이가 prefix + length가 된다")
    void generateApiKey_format() {
        // when
        String apiKey = apiKeyGenerator.generateApiKey();

        // then
        assertThat(apiKey).startsWith(API_KEY_PREFIX);
        assertThat(apiKey).hasSize(API_KEY_PREFIX.length() + API_KEY_LENGTH);
    }

    @Test
    @DisplayName("generateApiKey의 랜덤 부분은 URL-safe 문자만 포함한다")
    void generateApiKey_urlSafeCharactersOnly() {
        // when
        String apiKey = apiKeyGenerator.generateApiKey();

        // then
        String randomPart = apiKey.substring(API_KEY_PREFIX.length());
        assertThat(randomPart).matches(URL_SAFE_PATTERN);
    }

    @Test
    @DisplayName("generateApiKey는 연속 호출 시 서로 다른 값을 반환한다")
    void generateApiKey_uniqueness() {
        // when
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            generated.add(apiKeyGenerator.generateApiKey());
        }

        // then
        assertThat(generated).hasSize(100);
    }

    @Test
    @DisplayName("generateSecretKey는 48바이트를 패딩 없는 URL-safe Base64로 인코딩한 64자 문자열을 반환한다")
    void generateSecretKey_format() {
        // when
        String secretKey = apiKeyGenerator.generateSecretKey();

        // then: 48바이트 = 64자 (48 / 3 * 4), 패딩 없음
        assertThat(secretKey).hasSize(64);
        assertThat(secretKey).matches(URL_SAFE_PATTERN);
        assertThat(secretKey).doesNotContain("=");
    }

    @Test
    @DisplayName("generateSecretKey는 연속 호출 시 서로 다른 값을 반환한다")
    void generateSecretKey_uniqueness() {
        // when
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            generated.add(apiKeyGenerator.generateSecretKey());
        }

        // then
        assertThat(generated).hasSize(100);
    }
}

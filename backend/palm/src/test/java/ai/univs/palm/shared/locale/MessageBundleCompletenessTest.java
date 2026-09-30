package ai.univs.palm.shared.locale;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.palm.shared.web.enums.ErrorType;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.DisplayName;

/**
 * 응답 message 로 쓰는 키가 한국어·영어 메시지 파일에 모두 있는가 (UG-346).
 *
 * <p>{@code LocaleConfig} 가 {@code setUseCodeAsDefaultMessage(true)} 라, 키가 없으면 예외 대신
 * <b>키 이름이 그대로 errors.message 로 나간다</b>(예: {@code REQUIRED_IMAGE_FILE}). gate-web 은 모르는 코드면
 * errors.message 를 그대로 보여 주므로 화면에 키 이름이 노출됐다. 대상은 두 가지다.
 * <ul>
 *   <li>모든 {@link ErrorType} 이름 — 업무 예외의 message 키
 *   <li>소스의 {@code message = "KEY"} — 입력 검증(@NotBlank 등) 실패 시 핸들러가 이 키로 문구를 찾는다
 * </ul>
 */
@DisplayName("UG-346: 메시지 키가 한·영 번들에 모두 있다")
class MessageBundleCompletenessTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    /** 대문자 상수 형태의 검증 메시지 키만 본다. 일반 문장 메시지는 키가 아니다. */
    private static final Pattern VALIDATION_KEY = Pattern.compile("message\\s*=\\s*\"([A-Z][A-Z0-9_]*)\"");

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"messages_ko.properties", "messages_en.properties"})
    @DisplayName("모든 ErrorType 이름과 검증 메시지 키가 들어 있다")
    void 키가_모두_있다(String bundle) throws IOException {
        Set<String> keys = load(bundle).stringPropertyNames();

        Set<String> missing = new TreeSet<>();
        Arrays.stream(ErrorType.values()).map(Enum::name).filter(k -> !keys.contains(k)).forEach(missing::add);
        validationKeys().stream().filter(k -> !keys.contains(k)).forEach(missing::add);

        assertThat(missing)
                .as("%s 에 없는 키 — 없으면 키 이름이 사용자에게 그대로 보인다", bundle)
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"messages_ko.properties", "messages_en.properties"})
    @DisplayName("빈 문구가 없다")
    void 빈_문구가_없다(String bundle) throws IOException {
        Properties p = load(bundle);
        assertThat(p.stringPropertyNames().stream().filter(k -> p.getProperty(k).isBlank()).toList()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("스캔이 실제로 검증 키를 찾는다 — 엉뚱한 경로를 훑으면 0건으로 조용히 통과한다")
    void 스캔_범위() throws IOException {
        assertThat(validationKeys()).isNotEmpty();
    }

    private static Properties load(String bundle) throws IOException {
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(
                MessageBundleCompletenessTest.class.getClassLoader().getResourceAsStream(bundle), StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return p;
    }

    private static Set<String> validationKeys() throws IOException {
        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path f : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                Matcher m = VALIDATION_KEY.matcher(Files.readString(f));
                while (m.find()) found.add(m.group(1));
            }
        }
        return found;
    }
}

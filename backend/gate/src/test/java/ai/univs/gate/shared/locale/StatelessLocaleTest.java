package ai.univs.gate.shared.locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 언어 해석이 HttpSession 을 만들지 않는다 (UG-351). 요청마다 세션이 생겨 힙이 차던 문제의 회귀를 막는다.
 */
@DisplayName("UG-351: 세션 없는 언어 해석")
class StatelessLocaleTest {

    private final LocaleConfig config = new LocaleConfig();

    @RestController
    static class Probe {
        @GetMapping("/probe")
        String probe() {
            return LocaleContextHolder.getLocale().toLanguageTag();
        }
    }

    @ParameterizedTest(name = "[{0}] → {1}")
    @CsvSource({
            "ko, ko",
            "en, en",
            "en-US, en-US",                    // 지역은 유지한다
            "ko-KR, ko-KR",
            "'ko-KR,ko;q=0.9,en;q=0.8', ko-KR", // 브라우저 기본 헤더
            "'en-US,en;q=0.9,ko;q=0.8', en-US",
            "'ko,en;q=0.9', ko",               // 예전에는 「언어 미정」 → JVM 기본 언어
            "'en;q=0.1,ko;q=0.9', ko",         // q 순서대로
            "'ko;q=0.9', ko",
            "' en', en",                       // 앞 공백
            "'ja,ko;q=0.5', ko",               // 지원하지 않는 언어는 건너뛴다
            "'en,ko', en",                     // 같은 q 는 헤더 순서
            "'ko;q=0,en', en",                 // q=0 은 제외
            "'ko;q=0', en",
            "ja, en",                          // 지원 언어가 없으면 영어
            "'fr,de', en",
            "',', en",                         // 항목이 없으면 영어
            "'*', en",                       // * 는 서비스 기본값
            "'ja,*;q=0.5', en",
            // UG-352 항목별 해석 (auth UMS-36 과 같은 사례) — 한 항목이 깨져도 그 항목만 건너뛴다
            "'ko,,en', ko",                    // 빈 항목
            "ko_KR, ko-KR",                    // Java Locale.toString() 표기
            "'ko;q=0.9;foo=bar', ko",          // q 외 파라미터는 무시
            "'en;q=abc,ko;q=0.5', ko",         // q 가 숫자가 아니면 그 항목만 버린다
            "'en;q=1.5,ko;q=0.5', ko",         // q 범위 밖
            "'@@,ko', ko",                     // 형식이 깨진 태그
            "'en;;q=x', en",                   // 그 항목이 버려지고 남는 것이 없어 영어
            "'KO-kr', ko-KR",                  // 대소문자
            // 반박 리뷰 W1·W2 — auth 08a5e00 과 같게
            "ko-kor, en",                      // extlang 이 언어로 올라가면(kor) 지원 언어가 아니다
            "'en-zzz,ko;q=0.5', ko",           // 엉뚱한 Locale(zzz)로 나가지 않고 다음 항목으로
            "'ko;q=0,*', en",   // 기본 언어를 거부했으면 * 로도 고르지 않는다 (gate 기본은 en 이라 en)
            "'en-u-nu-arab', en",              // 확장은 버린다
            "'ko-Kore-KR', ko-KR",             // 스크립트는 버린다
            "'q=2', en",                       // 태그 형식이 아니면 건너뛴다
            "'ko;q=2;q=0.5', en",              // q 가 두 번이면 잘못된 항목 (auth L3)
            // auth 반박 리뷰 H1 — 500 이 나던 헤더. 던지지 않는다
            "';', en",
            "';;', en",
            "',;', en",
            "'ko,;', ko",
            "';,en', en",
            // L1·L2·L3
            "'en;q=0.8,ko ; q = 0.1', en",    // 공백이 있어도 q 다
            "'en;q=0.4,ko;q=0x1p-1', en",     // qvalue 문법만 받는다
            "'ko;q', en",                      // '=' 없는 q 는 잘못된 항목
            "'ko;q=0.5;q=1,en;q=0.1', en",     // q 두 번
            "'ko;Q=0.5,en;q=0.4', ko",
            "'ko;q=1.000,en;q=0.999', ko",
            "'ko;q=1.0001,en', en",
    })
    @DisplayName("UG-352: 항목별로 읽어 q 순서로 지원 언어(ko·en)를 고르고(지역 유지), 없으면 영어 — 세션은 만들지 않는다")
    void 해석(String header, String expected) {
        var request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", header);

        assertThat(config.localeResolver().resolveLocale(request).toLanguageTag()).isEqualTo(expected);
        assertThat(request.getSession(false)).as("해석이 세션을 만들면 안 된다").isNull();
    }

    @Test
    @DisplayName("빈 헤더는 없는 것과 같다 — 서비스 기본값")
    void 빈_헤더() {
        var request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "  ");

        assertThat(config.localeResolver().resolveLocale(request)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("헤더가 없으면 서비스 기본값(ENGLISH)")
    void 기본값() {
        assertThat(config.localeResolver().resolveLocale(new MockHttpServletRequest())).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("setLocale 은 지원하지 않는다 — 상태를 둘 곳이 없다")
    void 설정_불가() {
        assertThatThrownBy(() -> config.localeResolver().setLocale(
                new MockHttpServletRequest(), new MockHttpServletResponse(), Locale.KOREA))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("요청을 처리해도 세션이 생기지 않고 JSESSIONID 쿠키가 나가지 않는다 — 언어는 헤더대로 적용된다")
    void 요청_처리() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Probe())
                .setLocaleResolver(config.localeResolver())
                .build();

        MvcResult result = mvc.perform(get("/probe").header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getContentAsString()).isEqualTo("ko-KR");
    }

    @Test
    @DisplayName("메인 코드에 세션을 쓰는 언어 해석이 다시 들어오지 않는다")
    void 세션_사용_금지() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            String src = Files.readString(p);
                            // 주석 속 설명은 허용하고 실제 사용(import·생성)만 막는다
                            return src.contains("i18n.SessionLocaleResolver;") || src.contains("new SessionLocaleResolver(")
                                    || src.contains("i18n.CookieLocaleResolver;") || src.contains("new CookieLocaleResolver(")
                                    || src.contains("i18n.LocaleChangeInterceptor;") || src.contains(".setLocale(");
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .map(Path::toString)
                    .toList();
        }
        assertThat(offenders).as("언어를 세션·쿠키에 저장하면 요청마다 세션이 생긴다 (UG-351)").isEmpty();
    }

    @Test
    @DisplayName("설정 클래스로 컨텍스트를 띄우면 세션 없는 해석기만 있고, 세션을 만들던 인터셉터 빈은 없다")
    void 컨텍스트() {
        try (var ctx = new org.springframework.context.annotation.AnnotationConfigApplicationContext(LocaleConfig.class)) {
            assertThat(ctx.getBean("localeResolver")).isInstanceOf(HeaderLocaleResolver.class);
            assertThat(ctx.getBeansOfType(org.springframework.web.servlet.HandlerInterceptor.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("auth 반박 리뷰 H1: ';' 같은 헤더에도 요청이 500 으로 끝나지 않는다")
    void 깨진_헤더도_정상_응답() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Probe())
                .setLocaleResolver(config.localeResolver())
                .build();

        for (String header : List.of(";", ";;", ",;", ";,en")) {
            mvc.perform(get("/probe").header("Accept-Language", header))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("en"));
        }
    }
}

package ai.univs.palm.shared.locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.web.servlet.LocaleResolver;

/**
 * 요청의 {@code Accept-Language} 만 보고 언어를 정한다. <b>상태를 두지 않는다</b> (UG-351).
 *
 * <p>예전에는 {@code SessionLocaleResolver} 에 인터셉터가 요청마다 {@code setLocale} 을 불러 HttpSession 을 만들었다. API
 * 클라이언트와 Feign 은 JSESSIONID 를 돌려보내지 않으므로 요청마다 새 세션이 생겨 30분씩 힙에 남았다 — 온프레미스 부하 측정에서
 * gate 에 약 168만 개, Old 영역 100%, Full GC 로 처리량이 1/4 로 떨어졌다.
 *
 * <p><b>해석 규칙</b> (UG-352, auth UMS-36 과 같다 — 사용자 결정 2026-10-06):
 * <ul>
 *   <li>헤더가 없거나 비어 있으면 서비스 기본 언어.
 *   <li>있으면 q 우선순위대로 보고 처음 맞는 지원 언어(ko·en)를 고른다. {@code *} 는 기본 언어, {@code q=0} 은 제외한다.
 *   <li>지원 언어가 하나도 없거나 형식이 틀리면 영어.
 * </ul>
 * 예전 해석({@code Locale.forLanguageTag(헤더 전체)})은 {@code -} 로 나눈 올바른 앞부분까지만 읽어
 * {@code ko,en;q=0.9}·{@code en;q=0.1,ko;q=0.9}·{@code ko;q=0.9} 를 모두 「언어 미정」으로 만들었고, 문구가 JVM 기본 언어로
 * 나갔다. 핸들러 매핑 단계의 405·415 도 이제 요청 언어로 나간다(예전에는 인터셉터 전이라 기본 언어).
 */
public class HeaderLocaleResolver implements LocaleResolver {

    /** 메시지 번들이 있는 언어. 이 밖의 언어는 영어로 답한다. */
    static final List<Locale> SUPPORTED = List.of(Locale.KOREAN, Locale.ENGLISH);

    private final Locale defaultLocale;

    public HeaderLocaleResolver(Locale defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        String header = request.getHeader("Accept-Language");
        if (header == null || header.isBlank()) {
            return defaultLocale;
        }
        List<Locale.LanguageRange> ranges;
        try {
            ranges = Locale.LanguageRange.parse(header);   // q 내림차순, 같은 q 는 적힌 순서
        } catch (IllegalArgumentException e) {
            return Locale.ENGLISH;
        }
        for (Locale.LanguageRange range : ranges) {
            if (range.getWeight() <= 0) {
                continue;
            }
            String tag = range.getRange();
            if (tag.equals("*")) {
                return defaultLocale;
            }
            String language = tag.split("-", 2)[0];
            for (Locale supported : SUPPORTED) {
                if (supported.getLanguage().equals(language)) {
                    return supported;
                }
            }
        }
        return Locale.ENGLISH;
    }

    /** 요청마다 헤더로 정하므로 바꿀 상태가 없다. {@code AcceptHeaderLocaleResolver} 와 같은 계약이다. */
    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException("언어는 요청의 Accept-Language 로만 정한다 (UG-351)");
    }
}

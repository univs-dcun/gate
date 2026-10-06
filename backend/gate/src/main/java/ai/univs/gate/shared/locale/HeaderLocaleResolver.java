package ai.univs.gate.shared.locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.web.servlet.LocaleResolver;

/**
 * 요청의 {@code Accept-Language} 만 보고 언어를 정한다. <b>상태를 두지 않는다</b> (UG-351).
 *
 * <p>예전에는 {@code SessionLocaleResolver} 에 인터셉터가 요청마다 {@code setLocale} 을 불러 HttpSession 을 만들었다. API
 * 클라이언트와 Feign 은 JSESSIONID 를 돌려보내지 않으므로 요청마다 새 세션이 생겨 30분씩 힙에 남았다 — 온프레미스 부하 측정에서
 * gate 에 약 168만 개, Old 영역 100%, Full GC 로 처리량이 1/4 로 떨어졌다.
 *
 * <p>해석은 예전과 <b>똑같이</b> 둔다 — 헤더가 있으면 {@link Locale#forLanguageTag}(첫 태그까지만 읽는다:
 * {@code ko-KR,ko;q=0.9} → {@code ko}), 없으면 기본값. 오류 문구 언어가 이 변경으로 바뀌지 않게 하려는 것이다. q 값으로
 * 고르는 {@code AcceptHeaderLocaleResolver} 는 결과가 달라질 수 있는 헤더가 있어 쓰지 않았다.
 */
public class HeaderLocaleResolver implements LocaleResolver {

    private final Locale defaultLocale;

    public HeaderLocaleResolver(Locale defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        String lang = request.getHeader("Accept-Language");
        return lang != null ? Locale.forLanguageTag(lang) : defaultLocale;
    }

    /** 요청마다 헤더로 정하므로 바꿀 상태가 없다. {@code AcceptHeaderLocaleResolver} 와 같은 계약이다. */
    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException("언어는 요청의 Accept-Language 로만 정한다 (UG-351)");
    }
}

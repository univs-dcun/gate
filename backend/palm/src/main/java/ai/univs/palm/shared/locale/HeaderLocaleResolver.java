package ai.univs.palm.shared.locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.web.servlet.LocaleResolver;

/**
 * 요청의 {@code Accept-Language} 만 보고 언어를 정한다. <b>상태를 두지 않는다</b> (UG-351).
 *
 * <p>예전에는 {@code SessionLocaleResolver} 에 인터셉터가 요청마다 {@code setLocale} 을 불러 HttpSession 을 만들었다. API
 * 클라이언트와 Feign 은 JSESSIONID 를 돌려보내지 않으므로 요청마다 새 세션이 생겨 30분씩 힙에 남았다.
 *
 * <p><b>해석 규칙</b> (UG-352, auth UMS-36 의 {@code HeaderLocaleResolver.candidates} 와 같다 — 사용자 결정 2026-10-06):
 * <ul>
 *   <li>헤더가 없거나 비어 있으면 서비스 기본 언어.
 *   <li>쉼표로 나눠 <b>항목마다</b> 읽는다 — 한 항목이 깨져도 그 항목만 건너뛴다. 빈 항목({@code ko,,en})도 건너뛴다.
 *   <li>항목은 {@code ;} 로 나눠 첫 부분이 태그다. {@code _} 는 {@code -} 로 읽는다({@code ko_KR} → {@code ko-KR}, Java
 *       {@code Locale.toString()} 으로 헤더를 만드는 SDK). 태그는 {@link Locale.LanguageRange} 로 형식을 보고 깨지면 건너뛴다.
 *   <li>파라미터는 {@code q=} 만 본다(대소문자 무시). 숫자가 아니거나 0~1 밖이면 그 항목을 건너뛴다. 다른 파라미터는 무시한다.
 *   <li>q 내림차순, 같으면 헤더 순서. {@code q<=0} 은 제외한다. {@code *} 는 기본 언어다. 처음 맞는 지원 언어(ko·en)는
 *       지역을 유지해 돌려준다({@code ko-KR} → {@code ko-KR}).
 *   <li>읽을 수 있는 지원 언어가 없으면 영어.
 * </ul>
 */
public class HeaderLocaleResolver implements LocaleResolver {

    /** 메시지 번들이 있는 언어(기본 언어 부분). 이 밖의 언어는 영어로 답한다. */
    static final List<String> SUPPORTED_LANGUAGES = List.of("ko", "en");

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
        for (Candidate candidate : candidates(header)) {
            if (candidate.tag().equals("*")) {
                return defaultLocale;
            }
            String language = candidate.tag().split("-", 2)[0].toLowerCase(Locale.ROOT);
            if (SUPPORTED_LANGUAGES.contains(language)) {
                return Locale.forLanguageTag(candidate.tag());
            }
        }
        return Locale.ENGLISH;
    }

    /** 읽을 수 있는 항목만, q 내림차순(같으면 헤더 순서). q 가 0 이하인 항목은 빠진다. */
    static List<Candidate> candidates(String header) {
        List<Candidate> result = new ArrayList<>();
        String[] items = header.split(",");
        for (int order = 0; order < items.length; order++) {
            Candidate candidate = parseItem(items[order], order);
            if (candidate != null && candidate.q() > 0) {
                result.add(candidate);
            }
        }
        result.sort(Comparator.comparingDouble(Candidate::q).reversed().thenComparingInt(Candidate::order));
        return result;
    }

    private static Candidate parseItem(String item, int order) {
        String[] parts = item.split(";");
        String tag = parts[0].trim().replace('_', '-');
        if (tag.isEmpty()) {
            return null;
        }
        if (!tag.equals("*")) {
            try {
                new Locale.LanguageRange(tag);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        double q = 1.0;
        for (int i = 1; i < parts.length; i++) {
            String param = parts[i].trim();
            if (param.regionMatches(true, 0, "q=", 0, 2)) {
                try {
                    q = Double.parseDouble(param.substring(2).trim());
                } catch (NumberFormatException e) {
                    return null;
                }
                if (Double.isNaN(q) || q < 0 || q > 1) {
                    return null;
                }
            }
        }
        return new Candidate(tag, q, order);
    }

    record Candidate(String tag, double q, int order) {
    }

    /** 요청마다 헤더로 정하므로 바꿀 상태가 없다. {@code AcceptHeaderLocaleResolver} 와 같은 계약이다. */
    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException("언어는 요청의 Accept-Language 로만 정한다 (UG-351)");
    }
}

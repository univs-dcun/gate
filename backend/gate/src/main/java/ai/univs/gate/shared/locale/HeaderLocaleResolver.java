package ai.univs.gate.shared.locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleResolver;

/**
 * 요청의 {@code Accept-Language} 만 보고 언어를 정한다. <b>상태를 두지 않는다</b> (UG-351).
 *
 * <p>예전에는 {@code SessionLocaleResolver} 에 인터셉터가 요청마다 {@code setLocale} 을 불러 HttpSession 을 만들었다. API
 * 클라이언트와 Feign 은 JSESSIONID 를 돌려보내지 않으므로 요청마다 새 세션이 생겨 30분씩 힙에 남았다.
 *
 * <p><b>해석 규칙</b> (UG-352 — auth UMS-36 의 {@code HeaderLocaleResolver}(msa-scaffold 08a5e00)와 같은 로직이다. 사용자 결정
 * 2026-10-06. 어긋나면 한 요청이 gateway·auth·gate 를 지나며 언어가 바뀐다):
 * <ul>
 *   <li>헤더가 없거나 비어 있으면 서비스 기본 언어.
 *   <li>쉼표로 나눠 <b>항목마다</b> 읽는다 — 빈 항목·깨진 항목은 그 항목만 건너뛴다.
 *   <li>항목은 {@code ;} 로 나눠 첫 부분이 태그다. {@code _} 는 {@code -} 로 읽는다({@code ko_KR}). 태그는
 *       {@link Locale.LanguageRange} 로 형식을 본다.
 *   <li>파라미터는 {@code q=} 만 본다(대소문자 무시, 여러 번이면 마지막). 숫자가 아니거나 0~1 밖이면 그 항목을 건너뛴다.
 *   <li>q 내림차순, 같으면 헤더 순서. {@code q<=0} 은 「거부」다 — 고르지 않고, 기본 언어가 거부됐으면 {@code *} 로도 고르지
 *       않는다.
 *   <li>언어 판정은 {@link Locale#forLanguageTag} 결과의 언어로 한다 — 원문 첫 부분으로 보면 {@code ko-kor} 처럼 extlang 이
 *       언어로 올라가는 태그가 엉뚱한 Locale({@code kor})로 나가 하위 서비스와 언어가 어긋난다 (반박 리뷰 W1).
 *   <li>처음 맞는 지원 언어(ko·en)는 언어·지역만 남겨 돌려준다(스크립트·확장은 버린다).
 *   <li>읽을 수 있는 지원 언어가 없으면 영어.
 * </ul>
 */
public class HeaderLocaleResolver implements LocaleResolver {

    /** 메시지 번들이 있는 언어. 이 밖의 언어는 영어로 답한다. */
    static final Set<String> SUPPORTED = Set.of("ko", "en");

    private final Locale defaultLocale;

    public HeaderLocaleResolver(Locale defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        return resolve(request.getHeader("Accept-Language"));
    }

    Locale resolve(String header) {
        if (!StringUtils.hasText(header)) {
            return defaultLocale;
        }
        List<Candidate> candidates = candidates(header);
        Set<String> refused = new HashSet<>();
        for (Candidate candidate : candidates) {
            if (candidate.weight() <= 0 && !"*".equals(candidate.tag())) {
                refused.add(Locale.forLanguageTag(candidate.tag()).getLanguage());
            }
        }
        for (Candidate candidate : candidates) {
            if (candidate.weight() <= 0) {
                continue;
            }
            if ("*".equals(candidate.tag())) {
                if (refused.contains(defaultLocale.getLanguage())) {
                    continue;
                }
                return defaultLocale;
            }
            Locale locale = Locale.forLanguageTag(candidate.tag());
            if (SUPPORTED.contains(locale.getLanguage())) {
                return languageAndRegion(locale);
            }
        }
        return Locale.ENGLISH;
    }

    private static Locale languageAndRegion(Locale locale) {
        return locale.getCountry().isEmpty()
                ? Locale.of(locale.getLanguage())
                : Locale.of(locale.getLanguage(), locale.getCountry());
    }

    private static List<Candidate> candidates(String header) {
        List<Candidate> candidates = new ArrayList<>();
        String[] items = header.split(",");
        for (int i = 0; i < items.length; i++) {
            String[] parts = items[i].split(";");
            String tag = parts[0].trim().replace('_', '-');
            if (tag.isEmpty()) {
                continue;
            }
            double weight = 1.0;
            boolean validWeight = true;
            for (int p = 1; p < parts.length; p++) {
                String param = parts[p].trim();
                if (param.regionMatches(true, 0, "q=", 0, 2)) {
                    try {
                        weight = Double.parseDouble(param.substring(2).trim());
                    } catch (NumberFormatException e) {
                        validWeight = false;
                    }
                }
            }
            if (!validWeight || weight < 0 || weight > 1 || Double.isNaN(weight)) {
                continue;
            }
            if (!"*".equals(tag)) {
                try {
                    tag = new Locale.LanguageRange(tag).getRange();   // 형식 검사 (깨진 태그는 건너뛴다)
                } catch (IllegalArgumentException e) {
                    continue;
                }
            }
            candidates.add(new Candidate(tag, weight, i));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::weight).reversed()
                .thenComparingInt(Candidate::order));
        return candidates;
    }

    private record Candidate(String tag, double weight, int order) {
    }

    /** 요청마다 헤더로 정하므로 바꿀 상태가 없다. {@code AcceptHeaderLocaleResolver} 와 같은 계약이다. */
    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException("언어는 요청의 Accept-Language 로만 정한다 (UG-351)");
    }
}

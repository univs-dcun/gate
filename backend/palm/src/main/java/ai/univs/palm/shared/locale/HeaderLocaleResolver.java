package ai.univs.palm.shared.locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleResolver;

/**
 * 요청의 {@code Accept-Language} 만 보고 언어를 정한다. <b>상태를 두지 않는다</b> (UG-351).
 *
 * <p>예전에는 {@code SessionLocaleResolver} 에 인터셉터가 요청마다 {@code setLocale} 을 불러 HttpSession 을 만들었다. API
 * 클라이언트와 Feign 은 JSESSIONID 를 돌려보내지 않으므로 요청마다 새 세션이 생겨 30분씩 힙에 남았다.
 *
 * <p><b>해석 규칙</b> (UG-352 — auth UMS-36 의 {@code HeaderLocaleResolver}(msa-scaffold 08a5e00 + H1·L1~L3 수정)와 같은 로직이다. 사용자 결정
 * 2026-10-06. 어긋나면 한 요청이 gateway·auth·gate 를 지나며 언어가 바뀐다):
 * <ul>
 *   <li>헤더가 없거나 비어 있으면 서비스 기본 언어.
 *   <li>쉼표로 나눠 <b>항목마다</b> 읽는다 — 빈 항목·깨진 항목은 그 항목만 건너뛴다.
 *   <li>항목은 {@code ;} 로 나눠 첫 부분이 태그다. {@code _} 는 {@code -} 로 읽는다({@code ko_KR}). 태그는
 *       {@link Locale.LanguageRange} 로 형식을 본다.
 *   <li>파라미터는 이름이 {@code q} 인 것만 본다(첫 {@code =} 에서 나누고 이름은 trim·대소문자 무시). 값은 RFC 9110 qvalue
 *       문법만 받는다. {@code =} 없는 q, q 두 번, 문법이 아닌 값이면 그 항목을 건너뛴다.
 *   <li>q 내림차순, 같으면 헤더 순서. {@code q<=0} 은 「거부」다 — 고르지 않고, 기본 언어가 거부됐으면 {@code *} 로도 고르지
 *       않는다.
 *   <li>언어 판정은 {@link Locale#forLanguageTag} 결과의 언어로 한다 — 원문 첫 부분으로 보면 {@code ko-kor} 처럼 extlang 이
 *       언어로 올라가는 태그가 엉뚱한 Locale({@code kor})로 나가 하위 서비스와 언어가 어긋난다 (반박 리뷰 W1).
 *   <li>처음 맞는 지원 언어(ko·en)는 언어·지역만 남겨 돌려준다(스크립트·확장은 버린다).
 *   <li>읽을 수 있는 지원 언어가 없으면 영어. 어떤 헤더에도 던지지 않는다(예상 못 한 실패도 영어).
 * </ul>
 */
public class HeaderLocaleResolver implements LocaleResolver {

    /** 메시지 번들이 있는 언어. 이 밖의 언어는 영어로 답한다. */
    static final Set<String> SUPPORTED = Set.of("ko", "en");

    /** RFC 9110 qvalue. {@code Double.parseDouble} 은 0x1p-1·1e0·0.5f·+0.5 까지 받아 규칙이 흐려진다. */
    private static final Pattern QVALUE = Pattern.compile("^(0(\\.\\d{0,3})?|1(\\.0{0,3})?)$");

    private final Locale defaultLocale;

    public HeaderLocaleResolver(Locale defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        return resolve(request.getHeader("Accept-Language"));
    }

    /**
     * 어떤 헤더에도 던지지 않는다 — 언어 해석이 던지면 오류 응답을 만드는 중에도 다시 던져 요청이 500 으로 끝난다
     * (auth 반박 리뷰 H1: {@code ";"} 헤더 하나로 모든 경로가 500 이었다). 예상 못 한 실패는 영어로 답한다.
     */
    Locale resolve(String header) {
        try {
            return resolveUnsafe(header);
        } catch (RuntimeException e) {
            return Locale.ENGLISH;
        }
    }

    private Locale resolveUnsafe(String header) {
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
            // -1: ";" 나 ";;" 도 빈 태그 하나로 남긴다 — 없으면 빈 배열이 되어 parts[0] 에서 던진다 (auth H1)
            String[] parts = items[i].split(";", -1);
            String tag = parts[0].trim().replace('_', '-');
            if (tag.isEmpty()) {
                continue;
            }
            double weight = 1.0;
            boolean validWeight = true;
            boolean weightSeen = false;
            for (int p = 1; p < parts.length; p++) {
                String param = parts[p];
                int eq = param.indexOf('=');
                String name = (eq < 0 ? param : param.substring(0, eq)).trim();
                if (!name.equalsIgnoreCase("q")) {
                    continue;   // q 가 아닌 파라미터는 무시
                }
                // "ko ; q = 0.5" 도 q 다 (auth L1). '=' 없는 q, 두 번째 q, qvalue 문법이 아닌 값은 잘못된 항목 (L1·L2·L3)
                String value = eq < 0 ? null : param.substring(eq + 1).trim();
                if (weightSeen || value == null || !QVALUE.matcher(value).matches()) {
                    validWeight = false;
                    break;
                }
                weight = Double.parseDouble(value);
                weightSeen = true;
            }
            if (!validWeight) {
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

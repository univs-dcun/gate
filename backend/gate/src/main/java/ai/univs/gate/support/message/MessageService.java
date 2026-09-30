package ai.univs.gate.support.message;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.Locale;
import ai.univs.gate.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageSource messageSource;

    public String getMessage(ErrorType type) {
        return messageSource.getMessage(type.name(), null, LocaleContextHolder.getLocale());
    }

    public String getMessage(String typeString) {
        return messageSource.getMessage(typeString.toUpperCase(), null, LocaleContextHolder.getLocale());
    }

    /** 번들에 없는 실패 유형일 때 쓰는 대체 문구 키 (UG-346). */
    static final String UNKNOWN_FAILURE_REASON = "UNKNOWN_FAILURE_REASON";
    /** 라이브니스 실패 사유의 대체 문구 키 — 엔진이 준 문자열을 모를 때. */
    static final String LIVENESS_FAILED = "LIVENESS_FAILED";
    /** 번들에 키가 없음을 알아보는 표식. useCodeAsDefaultMessage(true) 라 키가 없어도 예외가 나지 않는다. */
    private static final String MISSING = "\u0000missing-message";

    /** 처음 본 미등록 유형만 한 번 경고한다 — 이력 목록은 같은 유형을 행마다 다시 찾는다. */
    private final Set<String> warnedUnknownTypes = ConcurrentHashMap.newKeySet();
    private static final int MAX_WARNED_TYPES = 256;

    /**
     * 실패 사유 문구. 빈 값이면 빈 문자열.
     *
     * <p><b>키가 없으면 대체 문구를 준다</b> (UG-346). 실패 유형은 하위 서비스나 외부 엔진(라이브니스 SDK)이 주는
     * 값이라 가능한 목록이 우리 코드에 없다 — 빌드 시점의 번들 가드로는 잡을 수 없다. 예전에는 키가 없으면 코드를
     * 그대로 돌려줘 화면에 {@code EYES CLOSED} 같은 값이 보였다. 이제는 공통 문구를 보이고, 처음 보는 유형을 한 번
     * 경고로 남겨 번들에 추가할 수 있게 한다.
     *
     * <p>UG-326: 번역 누락 한 건이 목록 전체를 500 으로 만들지 않게 예외를 삼키는 것은 그대로다.
     */
    public String getFailureMessageOrEmpty(String failureType) {
        return getFailureMessageOr(failureType, UNKNOWN_FAILURE_REASON);
    }

    /** 라이브니스 실패 사유. 엔진이 준 모르는 문자열이면 「라이브니스 검증에 실패했습니다」. */
    public String getLivenessFailureMessage(String failureType) {
        return getFailureMessageOr(failureType, LIVENESS_FAILED);
    }

    private String getFailureMessageOr(String failureType, String fallbackKey) {
        if (!StringUtils.hasText(failureType)) {
            return "";
        }
        String key = failureType.toUpperCase(Locale.ROOT);
        Locale locale = LocaleContextHolder.getLocale();
        try {
            String message = messageSource.getMessage(key, null, MISSING, locale);
            if (!MISSING.equals(message)) {
                return message;
            }
            // 상한을 둔다 — palm 엔진 message 는 자유 문장이라 가변 값이 섞이면 종류가 끝없이 늘 수 있다 (반박 리뷰).
            if (warnedUnknownTypes.size() < MAX_WARNED_TYPES && warnedUnknownTypes.add(key)) {
                log.warn("번들에 없는 실패 유형 — 대체 문구로 표시한다 (messages_*.properties 에 추가할 것): {}",
                        key.length() > 100 ? key.substring(0, 100) + "…" : key);
            }
            return messageSource.getMessage(fallbackKey, null, fallbackKey, locale);
        } catch (NoSuchMessageException e) {
            return failureType;
        }
    }
}

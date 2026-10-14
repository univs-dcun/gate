package ai.univs.match.shared.exception;

import ai.univs.match.shared.web.enums.ErrorType;
import java.util.Map;
import lombok.Getter;

@Getter
public class CustomFaceMatcherException extends RuntimeException {

    private final ErrorType errorType;

    /** 응답 errors.details 로 나간다. 없으면 null (UG-378) */
    private final Map<String, Object> details;

    public CustomFaceMatcherException(ErrorType errorType) {
        this(errorType, null);
    }

    public CustomFaceMatcherException(ErrorType errorType, Map<String, Object> details) {
        super();
        this.errorType = errorType;
        this.details = details;
    }
}

package ai.univs.match.shared.exception;

import ai.univs.match.shared.web.enums.ErrorType;
import lombok.Getter;

@Getter
public class CustomFaceMatcherException extends RuntimeException {

    private final ErrorType errorType;

    public CustomFaceMatcherException(ErrorType errorType) {
        super();
        this.errorType = errorType;
    }
}
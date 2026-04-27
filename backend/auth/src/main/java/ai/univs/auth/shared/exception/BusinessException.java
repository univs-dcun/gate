package ai.univs.auth.shared.exception;

import ai.univs.auth.shared.web.enums.ErrorType;
import lombok.Getter;

@Getter
public abstract class BusinessException extends RuntimeException {

    private final ErrorType errorType;

    public BusinessException(ErrorType errorType) {
        super(errorType.name());
        this.errorType = errorType;
    }
}

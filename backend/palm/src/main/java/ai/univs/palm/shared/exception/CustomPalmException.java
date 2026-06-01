package ai.univs.palm.shared.exception;

import ai.univs.palm.shared.web.enums.ErrorType;
import lombok.Getter;

@Getter
public class CustomPalmException extends RuntimeException {

    private final ErrorType errorType;

    public CustomPalmException(ErrorType errorType) {
        super(errorType.name());
        this.errorType = errorType;
    }
}

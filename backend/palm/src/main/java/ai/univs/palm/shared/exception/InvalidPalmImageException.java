package ai.univs.palm.shared.exception;

import ai.univs.palm.shared.web.enums.ErrorType;
import lombok.Getter;

@Getter
public class InvalidPalmImageException extends RuntimeException{

    private final ErrorType errorType;

    public InvalidPalmImageException(ErrorType errorType) {
        super(errorType.name());
        this.errorType = errorType;
    }
}

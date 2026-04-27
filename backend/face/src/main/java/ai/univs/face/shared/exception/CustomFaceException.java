package ai.univs.face.shared.exception;

import ai.univs.face.shared.web.enums.ErrorType;
import lombok.Getter;

@Getter
public class CustomFaceException extends RuntimeException {

    private final ErrorType errorType;

    public CustomFaceException(ErrorType errorType) {
        super(errorType.name());
        this.errorType = errorType;
    }
}

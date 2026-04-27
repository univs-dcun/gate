package ai.univs.face.shared.exception;

import ai.univs.face.shared.web.enums.ErrorType;
import lombok.Getter;

@Getter
public class InvalidFaceImageException extends RuntimeException{

    private final ErrorType errorType;

    public InvalidFaceImageException(ErrorType errorType) {
        super(errorType.name());
        this.errorType = errorType;
    }
}

package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;
import lombok.Getter;

@Getter
public class CustomAuthException extends RuntimeException {

    private final ErrorType processType;

    public CustomAuthException(ErrorType processType) {
        super(processType.name());
        this.processType = processType;
    }
}

package ai.univs.palm.shared.exception;

import lombok.Getter;

@Getter
public class InvalidPalmModuleException extends RuntimeException{

    private final String code;
    private final String type;

    public InvalidPalmModuleException(String code,
                                      String type,
                                      String message
    ) {
        super(message);
        this.code = code;
        this.type = type;
    }
}

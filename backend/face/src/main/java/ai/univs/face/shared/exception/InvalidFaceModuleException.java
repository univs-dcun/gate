package ai.univs.face.shared.exception;

import lombok.Getter;

@Getter
public class InvalidFaceModuleException extends RuntimeException{

    private final String code;
    private final String type;

    public InvalidFaceModuleException(String code,
                                      String type,
                                      String message
    ) {
        super(message);
        this.code = code;
        this.type = type;
    }
}

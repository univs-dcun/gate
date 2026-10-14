package ai.univs.face.shared.exception;

import java.util.Map;
import lombok.Getter;

@Getter
public class InvalidFaceModuleException extends RuntimeException{

    private final String code;
    private final String type;

    /** 하위 서비스 응답의 errors.details. 없으면 null (UG-378) */
    private final Map<String, Object> details;

    public InvalidFaceModuleException(String code,
                                      String type,
                                      String message
    ) {
        this(code, type, message, null);
    }

    public InvalidFaceModuleException(String code,
                                      String type,
                                      String message,
                                      Map<String, Object> details
    ) {
        super(message);
        this.code = code;
        this.type = type;
        this.details = details;
    }
}

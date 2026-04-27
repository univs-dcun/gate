package ai.univs.gate.shared.exception;

import lombok.Getter;

@Getter
public class CustomFeignException extends RuntimeException {

    private final String code;
    private final String type;

    public CustomFeignException(String code, String type, String message) {
        super(message);
        this.code = code;
        this.type = type;
    }
}

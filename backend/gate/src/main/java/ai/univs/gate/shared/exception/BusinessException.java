package ai.univs.gate.shared.exception;

import ai.univs.gate.shared.web.enums.ErrorType;
import lombok.Getter;

@Getter
public abstract class BusinessException extends RuntimeException {

    private final ErrorType errorType;

    /**
     * 응답 message 를 찾을 메시지 키. 기본은 {@code errorType} 이름이다.
     *
     * <p>같은 오류 코드인데 설치에 따라 안내가 달라야 할 때만 다른 키를 준다 (UG-111: 사설망을 허용한
     * 온프레미스에서 PJ-111 이 "외부에서 접근할 수 있는 주소여야 한다" 로 나가면 반대 안내가 된다).
     * 코드·type 은 그대로라 클라이언트 분기는 바뀌지 않는다.
     */
    private final String messageKey;

    public BusinessException(ErrorType errorType) {
        this(errorType, errorType.name());
    }

    protected BusinessException(ErrorType errorType, String messageKey) {
        super(errorType.name());
        this.errorType = errorType;
        this.messageKey = messageKey;
    }
}

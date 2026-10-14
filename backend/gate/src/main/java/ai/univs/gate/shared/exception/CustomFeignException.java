package ai.univs.gate.shared.exception;

import java.util.Map;
import lombok.Getter;

@Getter
public class CustomFeignException extends RuntimeException {

    private final String code;
    private final String type;

    /**
     * 하위 서비스 응답의 errors.details (UG-378). <b>내부용</b> — 하위의 식별자(match faceId 등)가 들어 있어 응답에
     * 싣지 않는다. 판단에 쓰고, 공개할 값은 gate 의 식별자로 바꿔 {@link #details} 에 둔다.
     */
    private final Map<String, Object> upstreamDetails;

    /** 응답 errors.details 로 나갈 값. gate 가 정한다 — 없으면 null (UG-378) */
    private final Map<String, Object> details;

    public CustomFeignException(String code, String type, String message) {
        this(code, type, message, null, null);
    }

    public CustomFeignException(String code, String type, String message, Map<String, Object> upstreamDetails) {
        this(code, type, message, upstreamDetails, null);
    }

    private CustomFeignException(String code, String type, String message,
                                 Map<String, Object> upstreamDetails, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.type = type;
        this.upstreamDetails = upstreamDetails;
        this.details = details;
    }

    /** 같은 오류에 공개 details 를 붙인 사본. 원래 예외는 cause 로 남긴다 */
    public CustomFeignException withDetails(Map<String, Object> details) {
        CustomFeignException copy = new CustomFeignException(code, type, getMessage(), upstreamDetails, details);
        copy.initCause(this);
        return copy;
    }
}

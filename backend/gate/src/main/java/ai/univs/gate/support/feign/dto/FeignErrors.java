package ai.univs.gate.support.feign.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeignErrors {
    private String code;
    private String type;
    private String message;
    /** 하위 서비스가 덧붙인 값. 없으면 null — 예: MATCH-003 의 {@code faceId} (UG-378). 응답에 그대로 싣지 않는다 */
    private Map<String, Object> details;

    public FeignErrors(String code, String type, String message) {
        this(code, type, message, null);
    }
}

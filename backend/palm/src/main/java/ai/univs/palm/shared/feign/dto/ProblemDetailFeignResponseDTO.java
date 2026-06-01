package ai.univs.palm.shared.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Innovatrics SmartFace API 에러 응답 (RFC 7807 ProblemDetail 포맷)
 * <pre>
 * {
 *   "type":   "https://...",
 *   "title":  "Bad Request",
 *   "status": 400,
 *   "detail": "The sent request was not valid"
 * }
 * </pre>
 * ignoreUnknown = true: palm 모듈이 추가 필드(errors 등)를 포함할 수 있으므로 무시
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProblemDetailFeignResponseDTO {
    private String type;
    private String title;
    private int status;
    private String detail;
}

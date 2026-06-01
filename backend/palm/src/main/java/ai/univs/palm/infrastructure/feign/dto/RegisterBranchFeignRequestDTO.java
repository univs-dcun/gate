package ai.univs.palm.infrastructure.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SmartFace POST /api/v1/Watchlists 요청 DTO
 * <pre>
 * {
 *   "displayName": "string (max 200)",
 *   "fullName":    "string (max 200)"
 * }
 * </pre>
 * threshold, palmThreshold, previewColor 는 클라이언트로부터 받지 않으므로 제외.
 * SmartFace 기본값 사용.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterBranchFeignRequestDTO {

    /** Watchlist 표시 이름 (max 200) */
    private String displayName;

    /** Watchlist 전체 이름 (max 200) */
    private String fullName;
}

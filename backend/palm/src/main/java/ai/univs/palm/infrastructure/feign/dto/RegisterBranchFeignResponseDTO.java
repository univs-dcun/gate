package ai.univs.palm.infrastructure.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SmartFace POST /api/v1/Watchlists 응답 DTO
 * <pre>
 * {
 *   "id":          "uuid",
 *   "displayName": "string",
 *   "fullName":    "string",
 *   "threshold":   0,
 *   "palmThreshold": 0,
 *   "previewColor": "#...",
 *   "createdAt":   "ISO8601",
 *   "updatedAt":   "ISO8601"
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegisterBranchFeignResponseDTO {

    /** Watchlist UUID (= branchId) */
    private String id;

    /** Watchlist 표시 이름 */
    private String displayName;

    /** Watchlist 전체 이름 */
    private String fullName;
}

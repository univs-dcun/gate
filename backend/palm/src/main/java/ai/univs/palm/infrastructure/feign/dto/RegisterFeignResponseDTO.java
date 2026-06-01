package ai.univs.palm.infrastructure.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SmartFace POST /api/v1/WatchlistMembers/Register 응답 DTO (WatchlistMember 객체)
 * <pre>
 * {
 *   "id":          "uuid",
 *   "displayName": "string",
 *   "watchlistIds": ["uuid"],
 *   ...
 * }
 * </pre>
 * ignoreUnknown = true: SmartFace 가 추가 필드를 포함할 수 있으므로 무시
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegisterFeignResponseDTO {

    /** WatchlistMember ID (등록 시 요청한 UUID = palmId) */
    private String id;

    /** 표시 이름 */
    private String displayName;

    /** 소속 watchlist ID 목록 */
    private List<String> watchlistIds;
}

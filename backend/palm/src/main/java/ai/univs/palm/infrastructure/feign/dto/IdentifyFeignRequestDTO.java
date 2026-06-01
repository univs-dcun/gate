package ai.univs.palm.infrastructure.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SmartFace POST /api/v1/Watchlists/SearchByPalm 요청 DTO
 * <pre>
 * {
 *   "image":        { "data": "base64..." },
 *   "watchlistIds": ["watchlistUuid"]
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentifyFeignRequestDTO {

    /** 팜 이미지 (base64 인코딩) */
    private ImageDTO image;

    /** 검색 대상 Watchlist ID 목록 (branchName = watchlist UUID) */
    private List<String> watchlistIds;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageDTO {
        /** base64 인코딩된 팜 이미지 바이트 */
        private String data;
    }
}

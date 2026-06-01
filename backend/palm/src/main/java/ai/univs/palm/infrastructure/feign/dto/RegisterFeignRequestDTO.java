package ai.univs.palm.infrastructure.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SmartFace POST /api/v1/WatchlistMembers/Register 요청 DTO
 * <pre>
 * {
 *   "id":           "uuid (our generated palmId)",
 *   "images":       [{ "modality": "Palm", "data": "base64...", "palmSide": "Unspecified" }],
 *   "watchlistIds": ["watchlistUuid (= branchName)"]
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterFeignRequestDTO {

    /** 등록할 WatchlistMember ID (우리 서버에서 생성한 UUID = palmId) */
    private String id;

    /** 등록 이미지 목록 (팜 전용이므로 항상 단일 Palm 이미지) */
    private List<RegistrationImageDTO> images;

    /** 소속 watchlist ID 목록 (단일 값 배열, branchName = watchlist UUID) */
    private List<String> watchlistIds;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegistrationImageDTO {

        /** 생체 모달리티 (Palm 고정) */
        private String modality;

        /** 팜 촬영 방향 */
        private String palmSide;

        /** base64 인코딩된 이미지 바이트 */
        private String data;
    }
}

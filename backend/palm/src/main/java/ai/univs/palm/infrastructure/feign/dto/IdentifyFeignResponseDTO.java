package ai.univs.palm.infrastructure.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SmartFace POST /api/v1/Watchlists/SearchByPalm 응답 DTO (배열의 단일 항목 = 감지된 팜 1개)
 * <p>
 * SmartFace 는 감지된 팜마다 하나의 항목을 배열로 반환하며,
 * 각 항목의 matchResults 에 임계치 이상인 매칭 결과가 포함된다.
 * <pre>
 * [
 *   {
 *     "matchResults": [
 *       {
 *         "score":              100,
 *         "watchlistMemberId": "uuid",
 *         "watchlistId":       "uuid",
 *         "watchlistDisplayName": "branchName",
 *         ...
 *       }
 *     ],
 *     "spoofCheckResult": { "performed": false, "passed": false, ... },
 *     "quality":    84.0,
 *     "confidence": 10000.0,
 *     ...
 *   }
 * ]
 * </pre>
 * → Feign 반환 타입: {@code List<IdentifyFeignResponseDTO>}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentifyFeignResponseDTO {

    /** 매칭 결과 목록 (임계치 이상만 포함, 없으면 빈 배열) */
    private List<MatchResultDTO> matchResults;

    /** 팜 감지 품질 점수 */
    private double quality;

    /** 팜 감지 신뢰도 */
    private double confidence;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MatchResultDTO {

        /** 유사도 점수 (0-100) */
        private double score;

        /** WatchlistMember ID (= 등록 시 사용한 palmId UUID) */
        private String watchlistMemberId;

        /** WatchlistMember 표시 이름 */
        private String displayName;

        /** 소속 Watchlist ID */
        private String watchlistId;

        /** 소속 Watchlist 표시 이름 (= branchName) */
        private String watchlistDisplayName;
    }
}

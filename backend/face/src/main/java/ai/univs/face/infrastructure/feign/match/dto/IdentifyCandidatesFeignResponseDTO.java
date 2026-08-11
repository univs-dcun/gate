package ai.univs.face.infrastructure.feign.match.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 1:N 후보 목록 매칭 응답 (UG-314).
 *
 * <p>유사도가 높은 순이다. 임계치 적용 전이라 미달 후보가 섞여 있다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdentifyCandidatesFeignResponseDTO {

    private List<Candidate> candidates;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Candidate {
        private String faceId;
        private String similarity;
    }
}

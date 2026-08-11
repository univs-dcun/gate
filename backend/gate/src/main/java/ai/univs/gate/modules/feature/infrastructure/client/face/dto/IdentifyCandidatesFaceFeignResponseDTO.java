package ai.univs.gate.modules.feature.infrastructure.client.face.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 특징점 기반 1:N 후보 목록 매칭 응답 (UG-314).
 *
 * <p>{@code candidates} 는 face-service 가 이미 임계치로 자른 결과다. 유사도는 0.0 ~ 1.0 이며
 * 백분율 변환은 gate 가 응답을 만들 때 한다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdentifyCandidatesFaceFeignResponseDTO {

    private String transactionUuid;
    private List<Candidate> candidates;
    private String threshold;
    private boolean result;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Candidate {
        private String faceId;
        private String similarity;
    }
}

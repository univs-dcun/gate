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

    /**
     * 임계치 통과 여부와 무관하게 가장 가까웠던 후보의 유사도 (0.0 ~ 1.0). 후보가 아예 없으면
     * {@code null}.
     *
     * <p>후보가 0명일 때 매칭 이력에 남길 값이다. 0 으로 눕히면 "아무도 근접하지 않았다" 와
     * "84.9 로 아깝게 미달했다" 가 이력에서 같아 보인다 — 기존 1:N 은 그 값을 남기고 있다.
     */
    private String nearestSimilarity;

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

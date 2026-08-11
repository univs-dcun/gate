package ai.univs.gate.modules.feature.infrastructure.client.face.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 특징점 기반 1:N 후보 목록 매칭 요청 (UG-314).
 *
 * <p>{@code threshold} 는 <b>0.0 ~ 1.0 도메인 스케일</b>이다. 클라이언트가 보낸 백분율은 gate 가
 * 이미 나눠서 넣는다 — face·match 는 백분율을 모른다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentifyCandidatesFaceFeignRequestDTO {

    private String branchName;
    private String descriptor;
    private Double threshold;
    private Integer maxCandidates;
    private String transactionUuid;
    private String clientId;
}

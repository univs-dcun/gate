package ai.univs.face.infrastructure.feign.match.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 1:N 후보 목록 매칭 요청 (UG-314).
 *
 * <p>임계치를 보내지 않는다 — match-server 는 상위 k건만 돌려주고 자르는 것은 이쪽 몫이다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentifyCandidatesFeignRequestDTO {

    private String branchName;
    private String descriptor;
    private Integer maxCandidates;
}

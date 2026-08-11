package ai.univs.match.application.result;

import java.util.List;

/**
 * 1:N 후보 목록 (UG-314).
 *
 * <p>유사도가 높은 순으로 정렬돼 있다. 임계치는 여기서 적용하지 않는다 — face-service 가
 * 클라이언트가 지정한 값으로 자른다.
 */
public record IdentifyCandidatesResult(
        List<IdentifyCandidateResult> candidates
) {
}

package ai.univs.match.application.result;

/**
 * 1:N 후보 목록의 한 건 (UG-314).
 *
 * <p>{@link IdentifyResult} 와 필드가 같지만 별도 타입으로 둔다. 목록의 원소는 "요청 하나의
 * 결과" 가 아니라 "후보 하나" 이고, 앞으로 순위·거리 같은 항목이 붙는다면 이쪽에만 붙는다.
 */
public record IdentifyCandidateResult(
        String faceId,
        String similarity
) {
}

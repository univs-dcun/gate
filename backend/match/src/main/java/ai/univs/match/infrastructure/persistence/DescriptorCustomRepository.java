package ai.univs.match.infrastructure.persistence;

import ai.univs.match.infrastructure.persistence.projection.MatchResultProjection;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface DescriptorCustomRepository {

    Double oneToOneMatch(byte[] requestDescriptor, byte[] target, int version);

    MatchResultProjection oneToManyMatch(Long branchId, byte[] requestDescriptor, int version);

    /**
     * 거리가 가까운 순으로 최대 {@code limit} 건 (UG-314).
     *
     * <p>임계치는 <b>받지 않는다.</b> 유사도 변환이 Platt scaling
     * {@code 1 / (1 + exp(A·distance + B))} 이고 지원하는 세 버전 모두 A 가 양수라, 거리가
     * 커지면 유사도는 반드시 작아진다. 즉 <b>거리 상위 k건 = 유사도 상위 k건</b>이고, 그중
     * 임계치 미달을 잘라내면 그게 곧 "임계치를 넘는 최대 k건" 이다 — 더 가져와 채울 대상이
     * 애초에 존재하지 않는다.
     *
     * <p>임계치 판정을 face-service 에 남기는 이유는 경계값 때문이다. 유사도는 소수점 5자리
     * 반올림 후 문자열로 비교되는데, 임계치를 SQL 로 내리려면 역변환으로 거리 컷을 계산해야
     * 하고 그건 <b>반올림 전</b> 값으로 자르는 것이라 기존 1:N 과 판정이 어긋난다.
     *
     * <p>성능상 손해도 없다. 이 쿼리는 브랜치 전 행에 {@code vlmatch()} 를 돌리는 풀스캔이라
     * WHERE 를 넣든 상위 k건만 받든 스캔량이 같다.
     */
    List<MatchResultProjection> oneToManyMatchTopK(
            Long branchId, byte[] requestDescriptor, int version, int limit);
}

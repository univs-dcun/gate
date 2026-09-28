package ai.univs.gate.support.reconcile;

import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureActionType;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결과를 모르는 등록을 찾는 조회 (UG-338).
 *
 * <p>제품 리포지토리와 분리한 이유는 {@code ProjectPurgeRepository} 와 같다 — 여기 조회는 소유나
 * {@code is_deleted} 를 보지 않고 이력의 <b>상태만</b> 본다. 제품 경로에서 잘못 집히면 안 된다.
 */
@Repository
@RequiredArgsConstructor
public class OrphanRegistrationRepository {

    private final EntityManager em;

    /**
     * 결과를 모르는 등록 행.
     *
     * <p>조건은 하나다 — <b>등록 행에 id 가 남아 있고 성공이 아니다.</b> {@code FeatureHistory} 가 그
     * 불변식을 지킨다: 하위가 코드를 주며 거절하면({@code fail}) id 를 지우고, 결과를 모르면(시작 상태로
     * 멈췄거나, 응답 없음·5xx 로 {@code failUpstream}, 하위가 자기 오류 유형을 알렸으면) 남긴다. 읽기 타임아웃은 하위가 등록을 끝냈는데
     * 응답만 늦은 경우일 수 있다.
     *
     * <ul>
     *   <li>UG-338 이전의 행에는 id 가 없다(하위가 발급했으므로). 무엇을 지울지 모르므로 건드리지 않는다.
     *   <li><b>{@code staleBefore} 보다 오래돼야 한다.</b> 아직 처리 중인 요청을 되돌리면 안 된다.
     *   <li><b>{@code oldest} 보다는 새로워야 한다.</b> 정리하지 못한 행을 무한히 다시 집지 않는다.
     * </ul>
     *
     * <p>프로젝트를 함께 읽는다 — 하위 삭제에 브랜치 이름과 소유자 id 가 필요하고, 이 조회 뒤에는
     * 트랜잭션이 없다.
     *
     * <p><b>{@code afterId} 커서로 한 바퀴씩 돈다</b> (UG-338 반박 리뷰). 가장 오래된 순으로 매번 앞에서부터
     * 집으면, 닫히지 않는 행(palm 의 "없음" 유형 미확인, 긴 장애)이 상한만큼 쌓였을 때 매 실행이 같은 행만
     * 다시 시도하고 뒤의 고아는 창(24시간)을 빠져나갈 때까지 차례가 오지 않는다. id 는 시퀀스라 시간순이다.
     */
    @Transactional(readOnly = true)
    public List<FeatureHistory> findStaleRegistrations(LocalDateTime staleBefore, LocalDateTime oldest,
                                                       long afterId, int limit) {
        return em.createQuery("""
                        SELECT h FROM FeatureHistory h JOIN FETCH h.project
                         WHERE h.actionType = :register
                           AND h.success = false
                           AND h.featureId IS NOT NULL
                           AND h.createdAt < :staleBefore
                           AND h.createdAt >= :oldest
                           AND h.id > :afterId
                         ORDER BY h.id ASC
                        """, FeatureHistory.class)
                .setParameter("register", FeatureActionType.REGISTER)
                .setParameter("staleBefore", staleBefore)
                .setParameter("oldest", oldest)
                .setParameter("afterId", afterId)
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * 그 id 의 특징점이 gate 에 있는가 — 소프트 삭제된 것까지.
     *
     * <p><b>안전장치다.</b> 성공 이력은 특징점 저장과 한 트랜잭션이라(UG-336) 특징점이 있으면 이력도
     * 성공이어야 한다. 그런데도 있다면 무언가 우리가 모르는 경로다 — 그때 하위를 지우면 살아 있는
     * 사용자의 템플릿을 지운다. 그래서 있으면 무조건 손대지 않는다.
     *
     * <p>프로젝트·유형으로 좁히지 않는다 — 안전장치는 넓을수록 안전하다. id 는 UUID 라 다른 프로젝트와
     * 겹칠 일이 없고, 겹친다면 그 자체가 모르는 경로다.
     */
    @Transactional(readOnly = true)
    public boolean featureExists(String featureId) {
        return !em.createQuery("""
                        SELECT f.id FROM BiometricFeature f WHERE f.featureId = :featureId
                        """, Long.class)
                .setParameter("featureId", featureId)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }
}

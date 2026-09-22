package ai.univs.gate.support.history;

import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.repository.FeatureHistoryRepository;
import ai.univs.gate.modules.feature.domain.repository.MatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이력을 호출자 트랜잭션과 <b>분리해서</b> 커밋한다 (UG-293).
 *
 * <p><b>무엇을 고치는가.</b> UG-280 은 이력이 사라지는 증상을 {@code noRollbackFor} 목록에
 * 예외 타입을 추가해 막았다. 그 방식은 "목록에 없는 예외가 하나라도 있으면 이력이 사라진다"
 * 는 성질을 그대로 남긴다. 실제로 반박 리뷰 세 번 동안 매번 새 구멍이 나왔다.
 *
 * <ol>
 *   <li>{@code ErrorDecoder} 는 300 이상 <b>응답이 도착했을 때만</b> 불린다 — 연결 거부·읽기
 *       타임아웃은 {@code RetryableException} 으로 디코더를 건너뛴다.
 *   <li>본문 디코딩 실패({@code FeignException.errorReading})는 {@code RetryableException} 이
 *       아니다. <b>HTTP 200 에서도</b> 난다.
 *   <li>HTTP 200 + {@code {"success":false,"data":null}} 는 예외조차 아니고 그냥 NPE 다.
 * </ol>
 *
 * <p>세 번 모두 "예외 타입을 하나 더 열거" 로 대응했다. 네 번째가 없다는 보장이 없고, 특히
 * <b>우리 코드의 버그</b>(NPE, {@code IllegalStateException})는 의도적으로 통과시키는데 그
 * 경우에도 이력은 사라진다 — 장애를 가장 관측해야 할 때 기록만 없어지는 셈이다.
 *
 * <p><b>어떻게 고치는가.</b> 이력 행을 호출자 트랜잭션 <b>밖에서</b> 커밋한다. 이미 커밋된
 * 행에는 호출자 쪽 롤백이 닿지 못하므로, 그 뒤에 어떤 예외가 나든 행이 남는다. 예외 타입을
 * 열거할 필요 자체가 없어진다.
 *
 * <pre>{@code
 * MatchHistory history = recorder.start(MatchHistory.builder()...build());  // 여기서 커밋된다
 * try {
 *     var data = faceService.identify(request);   // 무엇이 나든 위 행은 남는다
 *     history.success(feature, similarity);
 * } catch (RemoteCallException e) {
 *     history.failUpstream(e);
 *     recorder.finish(history);
 *     throw e;
 * }
 * recorder.finish(history);
 * }</pre>
 *
 * <p><b>{@code start} 와 {@code finish} 를 나눈 이유.</b> 하나로 묶으면 원격 호출이 그 안에
 * 들어가야 하는데, 그러면 호출 내내 DB 커넥션을 붙든다. 나누면 각 트랜잭션이 짧고, 그
 * 사이의 원격 호출은 트랜잭션 밖이다.
 *
 * <p><b>{@code finish} 를 부르지 못하면 어떻게 되나.</b> 행은 {@code start} 시점 상태
 * ({@code success=false}, {@code failure_type=null})로 남는다. 완벽하진 않지만 <b>행이
 * 사라지는 것보다 낫다</b> — 그 조합 자체가 "전이 전에 무언가 터졌다" 는 신호가 된다.
 * 예전 구조에서는 같은 상황이 흔적 없이 지워졌다.
 *
 * <p><b>왜 {@code REQUIRES_NEW} 인가.</b> 호출자에게 트랜잭션이 있으면 잠시 밀어 두고 새
 * 트랜잭션에서 커밋한 뒤 돌아온다. 호출자에게 트랜잭션이 없어도 동작한다. 즉 호출자가 어떤
 * 상태든 이력은 독립적으로 남는다.
 *
 * <p><b>돌려주는 엔티티는 준영속이다.</b> 이 트랜잭션이 끝나면 영속성 컨텍스트가 닫힌다.
 * 호출자는 그 객체의 상태를 바꾼 뒤 {@link #finish} 로 다시 넘긴다 — {@code merge} 가
 * 새 트랜잭션에서 붙여 준다.
 */
@Component
@RequiredArgsConstructor
public class HistoryRecorder {

    private final MatchHistoryRepository matchHistoryRepository;
    private final FeatureHistoryRepository featureHistoryRepository;

    /** 인증 이력을 즉시 커밋한다. 돌아온 객체는 준영속이다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MatchHistory start(MatchHistory history) {
        return matchHistoryRepository.save(history);
    }

    /** 특징점 사건 이력을 즉시 커밋한다. 돌아온 객체는 준영속이다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FeatureHistory start(FeatureHistory history) {
        return featureHistoryRepository.save(history);
    }

    /**
     * 상태 전이(성공·실패)를 별도 트랜잭션에서 커밋한다.
     *
     * <p>전이가 호출자 트랜잭션 안에 있으면, 그 트랜잭션이 나중에 롤백될 때 전이도 함께
     * 사라져 행이 {@code start} 시점 상태로 되돌아간다 — 실패 사유가 지워진다는 뜻이다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(MatchHistory history) {
        matchHistoryRepository.save(history);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(FeatureHistory history) {
        featureHistoryRepository.save(history);
    }
}

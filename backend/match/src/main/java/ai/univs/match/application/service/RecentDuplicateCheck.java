package ai.univs.match.application.service;

import static ai.univs.match.shared.utils.SimilarityCalculator.getSimilarityByDistance;
import static ai.univs.match.shared.utils.SimilarityCalculator.parseDoubleSimilarity;

import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.infrastructure.persistence.DescriptorCustomRepository;
import ai.univs.match.infrastructure.persistence.DescriptorRepository;
import ai.univs.match.infrastructure.persistence.projection.MatchResultProjection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 얼굴 동시 등록을 커밋 뒤에 걸러 낸다 (UG-374).
 *
 * <p><b>왜 필요한가.</b> 등록은 1:N 중복 검사 → INSERT → 커밋이다. READ COMMITTED 에서는 동시에 진행 중인 다른 등록의
 * INSERT 가 보이지 않아, 같은 얼굴 요청들이 모두 "중복 없음" 을 보고 각자 저장했다(scaling 134, 동시 16 에서 6~16건 저장).
 * 빈 브랜치는 중복 검사 자체를 건너뛰어 더 잘 생겼다.
 *
 * <p><b>어떻게.</b> 등록 트랜잭션이 커밋된 직후, 새 트랜잭션에서
 * <ol>
 *   <li>브랜치 행을 잠근다({@link DescriptorCustomRepository#lockBranchForRecheck}) — 같은 브랜치의 재검사를 한 번에 하나씩 돌린다.
 *   <li>같은 브랜치에서 최근 {@link #WINDOW} 안에 저장된 같은 버전 특징점과 비교한다(자기 제외). 브랜치 전체가 아니라
 *       최근 행만 인덱스(V6)로 읽는다.
 *   <li>임계치({@link DuplicateService#DUPLICATE_SIMILARITY}) 이상인 것이 <b>보이면</b> 방금 저장한 자기 행을 지운다 —
 *       호출자는 MATCH-003 으로 끝낸다.
 * </ol>
 *
 * <p><b>왜 정확한가 — 재검사를 줄 세우는 이유.</b> 재검사가 브랜치마다 하나씩 돌면, 같은 얼굴 무리에서 재검사를 통과해
 * 남는 행은 하나다. 남은 행 X 는 자기 재검사 때 비슷한 행을 못 봤다. 그 뒤에 재검사하는 같은 얼굴 Y 는 X 를 반드시 본다 —
 * X 는 Y 의 재검사 전에 커밋됐고(X 의 재검사가 X 의 커밋 뒤, Y 의 재검사보다 앞) 지워지지 않았다. 그래서 Y 는 물러난다.
 * 그리고 하나는 반드시 남는다 — 마지막으로 재검사하는 행이 무엇을 본다면 그것은 이미 재검사를 통과해 남은 행이다.
 *
 * <p><b>한계 — 유사도는 이행적이지 않다.</b> 위 증명은 "같은 얼굴" 이 서로 모두 비슷한 묶음이라고 본다. 임계치 근처의
 * 서로 다른 세 사람 Y~Z, Z~W(Y≁W)가 같은 브랜치에 60초 안에 동시에 오면, 재검사 순서에 따라 Y 가 아직 재검사 전인 Z 를
 * 보고 물러난 뒤 Z 도 W 때문에 물러나 W 만 남을 수 있다 — Y 는 MATCH-003 을 받았는데 비슷한 얼굴이 남지 않는다. 사전 1:N
 * 검사에도 같은 구멍이 있다. 막으려면 재검사를 통과한 행에 표시를 남기고 그 행하고만 비교해야 한다(컬럼 추가). 조건이
 * 겹칠 일이 드물고, 결과도 "중복 저장" 이 아니라 "재시도하면 성공하는 거절" 이라 받아들였다 (반박 리뷰 M1).
 *
 * <p>잠금 없이 "id 가 작은 쪽이 남는다" 로 정하는 방식은 틀린다. id 가 작은 A 가 INSERT 뒤 커밋이 늦는 사이 B 가 커밋·재검사를
 * 끝내면 B 는 A 를 못 보고 남는다. 이어서 A 는 B 를 보지만 B 가 더 크니 자기도 남는다 — 두 건.
 *
 * <p><b>비용.</b> 잠그는 동안 하는 일은 최근 몇 건의 비교와 (중복이면) 삭제 하나다. 1:N 전체 검사(갤러리 100만이면 약 1초)는
 * 잠금 밖에서 그대로 돈다. 같은 브랜치의 등록만 이 짧은 구간에서 줄을 서고, 다른 브랜치·인증·식별은 영향이 없다. 잠금은
 * 특징점 INSERT 를 막지 않는다({@link DescriptorCustomRepository#lockBranchForRecheck}).
 *
 * <p><b>재검사가 실패하면 등록을 유지한다.</b> 잠금 대기 상한(1초)·DB 오류 등으로 재검사를 끝내지 못하면 WARN 을 남기고
 * 성공으로 돌려준다 — UG-374 이전과 같다(중복이 남을 수 있다). 여기서 실패로 돌려주면 match 에는 행이 남았는데 gate 는 실패로
 * 기록해 match 에만 있는 행이 생긴다. 중복보다 그쪽이 정리하기 어렵다. 단 자기 행이 이미 지워졌으면(삭제 커밋 중 연결
 * 끊김) 물린 것으로 본다 — {@link #withdrawnDespiteFailure}.
 *
 * <p><b>남는 위험.</b> 커밋부터 자기 삭제까지 짧은 동안 물러날 행이 1:N 후보로 보일 수 있다 — 그 faceId 는 gate 에 아직 없어
 * gate 가 사용자 없음으로 끝낸다.
 */
@Slf4j
@Component
public class RecentDuplicateCheck {

    /**
     * 「최근」의 폭. 경합한 상대의 {@code created_at} 은 상대가 INSERT 직전에 정한 값이고, 상대는 그 뒤 INSERT·커밋만 하면 된다.
     * 그래서 상대가 우리 재검사에 보일 때 그 값은 많아야 몇 초 전이다. match 쿼리 상한(3.5초)·풀 대기(1.5초)·인스턴스 간
     * 시계 차이를 넉넉히 덮는다. 넓혀도 정확성은 같고 비교 행만 는다.
     *
     * <p>{@code created_at} 은 저장한 인스턴스의 시계, 기준 시각은 재검사하는 인스턴스의 시계다. match 를 여러 대 띄우면 서로의
     * 시계가 NTP 로 맞아 있어야 한다 — 차이가 약 50초를 넘으면 상대 행을 놓친다 (반박 리뷰 L3).
     */
    static final Duration WINDOW = Duration.ofSeconds(60);

    /** 특징점 비교 개수 — {@link IdentifyService} 와 같다. */
    private static final int MATCH_VERSION = 512;

    private final DescriptorCustomRepository descriptorCustomRepository;
    private final DescriptorRepository descriptorRepository;
    private final TransactionTemplate recheckTransaction;

    /** 테스트가 시각을 고정한다. {@code created_at} 과 같은 UTC {@link LocalDateTime} 이다. */
    Supplier<LocalDateTime> now = () -> LocalDateTime.now(ZoneOffset.UTC);

    public RecentDuplicateCheck(DescriptorCustomRepository descriptorCustomRepository,
                                DescriptorRepository descriptorRepository,
                                PlatformTransactionManager transactionManager) {
        this.descriptorCustomRepository = descriptorCustomRepository;
        this.descriptorRepository = descriptorRepository;
        this.recheckTransaction = new TransactionTemplate(transactionManager);
    }

    /**
     * 물린 결과 (UG-378).
     *
     * @param duplicateFaceId 물린 까닭인 같은 얼굴의 faceId. 재검사가 실패했는데 자기 행이 이미 지워진 경우처럼 상대를
     *                        모르면 null 이다. 상대 행도 곧 물러날 수 있어(아직 재검사 전) 존재를 보장하지 않는다.
     */
    public record Withdrawal(String duplicateFaceId) {
    }

    /**
     * 방금 커밋한 특징점을 재검사하고, 같은 얼굴이 보이면 지운다. 등록 트랜잭션이 커밋된 <b>뒤에</b>, 트랜잭션 밖에서 부른다.
     *
     * @return 값이 있으면 지웠다 — 호출자는 MATCH-003 으로 끝낸다. 비어 있으면 등록을 유지한다(재검사 실패 포함).
     */
    public Optional<Withdrawal> withdrawIfDuplicated(Long branchId, Long descriptorId, DescriptorDetail descriptorDetail) {
        try {
            return Optional.ofNullable(recheckTransaction.execute(
                    status -> recheck(branchId, descriptorId, descriptorDetail)));
        } catch (RuntimeException e) {
            return withdrawnDespiteFailure(branchId, descriptorId, e);
        }
    }

    /**
     * 재검사가 실패했다. 대개(잠금 대기 상한·DB 오류) 자기 행은 그대로라 등록을 유지한다. 다만 자기 행을 지우는 커밋 중에
     * 연결이 끊기면 지워졌는지 모른다 — 그때 성공으로 돌려주면 gate 에만 있는 faceId 가 남는다. 그래서 새로 읽어 본다:
     * 없으면 물린 것(MATCH-003), 있거나 읽기도 실패하면 유지.
     */
    private Optional<Withdrawal> withdrawnDespiteFailure(Long branchId, Long descriptorId, RuntimeException cause) {
        try {
            if (!descriptorRepository.existsById(descriptorId)) {
                log.warn("같은 얼굴 재검사가 실패했지만 자기 행은 이미 지워졌다 — 물린 것으로 본다. branchId={}, descriptorId={}",
                        branchId, descriptorId, cause);
                return Optional.of(new Withdrawal(null));
            }
        } catch (RuntimeException readFailure) {
            cause.addSuppressed(readFailure);
        }
        log.warn("같은 얼굴 재검사를 끝내지 못했다 — 등록은 유지한다(중복이 남을 수 있다). branchId={}, descriptorId={}",
                branchId, descriptorId, cause);
        return Optional.empty();
    }

    /** @return 물렸으면 그 결과, 유지하면 null — TransactionTemplate 이 Optional 을 감싸지 않게 null 로 돌려준다 */
    private Withdrawal recheck(Long branchId, Long descriptorId, DescriptorDetail descriptorDetail) {
        descriptorCustomRepository.lockBranchForRecheck(branchId);

        Optional<MatchResultProjection> nearest = descriptorCustomRepository.nearestRecent(
                branchId,
                descriptorId,
                now.get().minus(WINDOW),
                descriptorDetail.descriptorSpec().getVersion(),
                descriptorDetail.descriptorBody(),
                MATCH_VERSION);
        if (nearest.isEmpty()) {
            return null;
        }

        double similarity = parseDoubleSimilarity(
                getSimilarityByDistance(nearest.get().getDistance(), descriptorDetail.descriptorSpec()));
        if (similarity < DuplicateService.DUPLICATE_SIMILARITY) {
            return null;
        }

        descriptorRepository.deleteById(descriptorId);
        log.warn("같은 얼굴이 동시에 등록돼 방금 저장한 특징점을 물린다 — similarity={}, branchId={}, descriptorId={}",
                similarity, branchId, descriptorId);
        return new Withdrawal(nearest.get().getFaceId());
    }
}

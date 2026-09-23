package ai.univs.gate.support.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.infrastructure.persistence.MatchHistoryRepositoryImpl;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.support.jpa.JpaSliceTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이력이 호출자 롤백에서 살아남는가 (UG-293).
 *
 * <p><b>이 테스트가 UG-293 의 전부다.</b> 이전 가드({@code RemoteCallRollbackGuardTest})는
 * 소스에 {@code noRollbackFor} 선언이 있는지를 문자열로 확인했다. 그것은 "열거한 예외에서는
 * 롤백하지 않는다" 를 지킬 뿐, <b>열거하지 않은 예외</b>에서 무슨 일이 나는지는 보지 못한다 —
 * 그리고 UG-293 이 고치려는 것이 정확히 그 부분이다.
 *
 * <p>그래서 여기서는 선언이 아니라 <b>실제 트랜잭션 동작</b>을 본다. 트랜잭션을 열고, 이력을
 * 기록하고, 아무 예외나 던져 롤백시킨 뒤, 행이 남아 있는지 DB 에서 확인한다.
 *
 * <p>클래스에 {@code NOT_SUPPORTED} 를 건 이유는 {@code @DataJpaTest} 가 테스트마다 트랜잭션을
 * 열고 끝에 롤백하기 때문이다. 그 안에서는 무엇을 커밋해도 사라져 검증이 성립하지 않는다.
 * 대신 {@link TransactionTemplate} 로 필요한 트랜잭션을 직접 연다.
 */
@JpaSliceTest
@Import({HistoryRecorder.class, MatchHistoryRepositoryImpl.class,
        ai.univs.gate.modules.feature.infrastructure.persistence.FeatureHistoryRepositoryImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("UG-293: 이력 커밋 경계")
class HistoryRecorderSliceTest {

    private static final String TX_UUID = "ug293-tx";

    @Autowired private HistoryRecorder recorder;
    @Autowired private EntityManager em;
    @Autowired private TransactionTemplate tx;

    private Long projectId;

    @BeforeEach
    void setUp() {
        projectId = tx.execute(status -> {
            Project p = Project.builder()
                    .accountId(100L).projectName("테스트").branchName("br-293")
                    .isDeleted(false).status(ProjectStatus.ACTIVE).build();
            em.persist(p);
            return p.getId();
        });
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> {
            em.createQuery("DELETE FROM MatchHistory h WHERE h.transactionUuid = :uuid")
                    .setParameter("uuid", TX_UUID).executeUpdate();
            em.createQuery("DELETE FROM FeatureHistory h WHERE h.transactionUuid = :uuid")
                    .setParameter("uuid", TX_UUID).executeUpdate();
            em.createQuery("DELETE FROM Project p WHERE p.id = :id")
                    .setParameter("id", projectId).executeUpdate();
        });
    }

    private MatchHistory 이력() {
        Project project = tx.execute(status -> em.find(Project.class, projectId));
        return MatchHistory.builder()
                .project(project)
                .matchType(MatchType.LIVENESS)
                .featureType(FeatureType.FACE)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(true)
                .success(false)
                .transactionUuid(TX_UUID)
                .consentSnapshot(false)
                .build();
    }

    private List<MatchHistory> 남은_이력() {
        return tx.execute(status -> em.createQuery(
                        "SELECT h FROM MatchHistory h WHERE h.transactionUuid = :uuid",
                        MatchHistory.class)
                .setParameter("uuid", TX_UUID).getResultList());
    }

    /**
     * 본 검증. <b>열거하지 않은 예외</b>에서도 이력이 남는가.
     *
     * <p>{@code IllegalStateException} 은 어떤 {@code noRollbackFor} 목록에도 없었다. 예전
     * 구조에서는 이 예외 하나로 이력 행이 통째로 사라졌다 — 그리고 그것이 UG-280 의 반박
     * 리뷰가 세 번 연속 새 구멍을 찾아낸 이유다.
     */
    @Test
    @DisplayName("호출자가 어떤 예외로 롤백해도 이력은 남는다")
    void 호출자_롤백에도_남는다() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            recorder.start(이력());
            throw new IllegalStateException("우리 코드의 버그 — 어떤 noRollbackFor 목록에도 없다");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(남은_이력())
                .as("이 단언이 깨지면 UG-280 이 세 번 겪은 실패 모드가 그대로 돌아온 것이다")
                .hasSize(1);
    }

    /**
     * 대조군. 같은 조건에서 <b>호출자 트랜잭션 안에</b> 저장하면 사라진다.
     *
     * <p>이것이 성립하지 않으면 위 테스트는 아무것도 증명하지 못한다 — 애초에 롤백이 일어나지
     * 않는 환경일 수도 있기 때문이다.
     */
    @Test
    @DisplayName("같은 조건에서 호출자 트랜잭션 안에 저장하면 사라진다 — 대조군")
    void 대조군_호출자_안에_저장하면_사라진다() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            em.persist(이력());
            em.flush();
            throw new IllegalStateException("같은 예외");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(남은_이력())
                .as("롤백이 실제로 일어나는 환경임을 보인다")
                .isEmpty();
    }

    /**
     * 실패 사유 전이도 별도 트랜잭션이어야 한다.
     *
     * <p>전이가 호출자 트랜잭션 안에 있으면, 그 트랜잭션이 나중에 롤백될 때 사유가 지워져
     * 행이 {@code start} 시점 상태로 되돌아간다 — 행은 남지만 "왜 실패했는지" 가 사라진다.
     */
    @Test
    @DisplayName("실패 사유도 호출자 롤백에서 살아남는다")
    void 실패_사유도_남는다() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            MatchHistory history = recorder.start(이력());
            history.failUpstream(new RemoteCallException(503));
            recorder.fail(history);
            throw new IllegalStateException("전이 뒤에 터진다");
        })).isInstanceOf(IllegalStateException.class);

        List<MatchHistory> found = 남은_이력();
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getFailureType())
                .as("사유가 지워지면 행만 남고 조사할 것이 없다")
                .isNotNull();
        assertThat(found.get(0).getUpstreamStatus()).isEqualTo(503);
    }

    @Test
    @DisplayName("성공 전이도 커밋된다 — 정상 경로가 깨지지 않았다")
    void 성공_전이도_커밋된다() {
        tx.executeWithoutResult(status -> {
            MatchHistory history = recorder.start(이력());
            history.success(BigDecimal.valueOf(0.99));
            recorder.succeed(history);
        });

        List<MatchHistory> found = 남은_이력();
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getSuccess()).isTrue();
    }

    /**
     * 성공 전이는 호출자 트랜잭션과 함께 사라져야 한다 (반박 리뷰 지적).
     *
     * <p>초판은 성공도 별도 트랜잭션이라 즉시 커밋됐다. 그러면 특징점 등록처럼 이력과 실제
     * 데이터를 함께 쓰는 경로에서 순서가 뒤집힌다 — "등록 성공" 이력이 먼저 커밋되고, 그 뒤
     * 바깥이 롤백되면 {@code biometric_feature} 행은 없는데 이력만 성공으로 남는다.
     *
     * <p>행 자체는 남는다({@code start} 가 커밋해 뒀다). 남되 {@code success=false} 이고,
     * 그것이 사실이다 — 그 일은 완료되지 않았다.
     */
    @Test
    @DisplayName("성공 전이는 호출자가 롤백하면 함께 사라진다 — 행은 남고 성공만 취소된다")
    void 성공_전이는_호출자와_함께_롤백된다() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            MatchHistory history = recorder.start(이력());
            history.success(BigDecimal.valueOf(0.99));
            recorder.succeed(history);
            throw new IllegalStateException("성공 전이 뒤에 실제 데이터 저장이 실패한다");
        })).isInstanceOf(IllegalStateException.class);

        List<MatchHistory> found = 남은_이력();
        assertThat(found).as("행은 남아야 한다 — 시도가 있었던 것은 사실이다").hasSize(1);
        assertThat(found.get(0).getSuccess())
                .as("성공이 함께 커밋되면 롤백된 일을 '성공했다' 고 기록하게 된다")
                .isFalse();
    }

    /**
     * 호출자에게 트랜잭션이 없어도 동작한다.
     *
     * <p>{@code REQUIRES_NEW} 는 바깥 트랜잭션이 없으면 그냥 새로 연다. 유스케이스에서
     * {@code @Transactional} 이 사라지는 리팩터링이 와도 이력은 계속 남아야 한다.
     */
    @Test
    @DisplayName("호출자 트랜잭션이 없어도 커밋된다")
    void 트랜잭션_없이도_남는다() {
        recorder.start(이력());

        assertThat(남은_이력()).hasSize(1);
    }

    /**
     * 특징점 사건 이력도 같은 경계를 쓴다.
     *
     * <p>{@code MatchHistory} 만 덮으면 {@code FeatureHistory} 쪽 오버로드를 통째로 no-op 으로
     * 만들어도 아무 테스트도 깨지지 않는다 — 반박 리뷰가 실제로 그 변이를 심어 확인했다.
     * 등록·삭제 이력이 커밋되지 않으면 대시보드 등록 건수가 조용히 미달한다.
     */
    @Test
    @DisplayName("특징점 사건 이력도 호출자 롤백에서 살아남고, 성공은 함께 롤백된다")
    void 특징점_이력도_같은_경계다() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            FeatureHistory history = recorder.start(특징점_이력());
            history.failUpstream(new RemoteCallException(503));
            recorder.fail(history);
            throw new IllegalStateException("전이 뒤에 터진다");
        })).isInstanceOf(IllegalStateException.class);

        List<FeatureHistory> found = 남은_특징점_이력();
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getFailureType())
                .as("실패 사유가 지워지면 행만 남고 조사할 것이 없다")
                .isNotNull();
        assertThat(found.get(0).getUpstreamStatus()).isEqualTo(503);
    }

    @Test
    @DisplayName("특징점 사건의 성공 전이는 커밋된다 — succeed 가 no-op 이면 여기서 깨진다")
    void 특징점_이력_성공도_커밋된다() {
        tx.executeWithoutResult(status -> {
            FeatureHistory history = recorder.start(특징점_이력());
            history.successDelete();
            recorder.succeed(history);
        });

        List<FeatureHistory> found = 남은_특징점_이력();
        assertThat(found).hasSize(1);
        assertThat(found.get(0).isSuccess())
                .as("succeed 오버로드가 비면 등록·삭제가 전부 실패로 기록된다")
                .isTrue();
    }

    private FeatureHistory 특징점_이력() {
        Project project = tx.execute(status -> em.find(Project.class, projectId));
        return FeatureHistory.register(project, FeatureType.FACE, false, "img/x", TX_UUID, false);
    }

    private List<FeatureHistory> 남은_특징점_이력() {
        return tx.execute(status -> em.createQuery(
                        "SELECT h FROM FeatureHistory h WHERE h.transactionUuid = :uuid",
                        FeatureHistory.class)
                .setParameter("uuid", TX_UUID).getResultList());
    }

    /**
     * 행이 두 번 생기지 않는다.
     *
     * <p>{@code finish} 가 {@code save} 를 다시 부르므로, 준영속 엔티티가 merge 가 아니라
     * persist 로 처리되면 같은 사건이 두 행이 된다. 이력 건수가 곧 지표이므로 중복은 조용한
     * 오염이다.
     */
    @Test
    @DisplayName("start 뒤 finish 를 여러 번 불러도 행은 하나다")
    void 중복_행이_생기지_않는다() {
        MatchHistory history = recorder.start(이력());
        history.fail(BigDecimal.ZERO, "NOT_MATCH");
        recorder.fail(history);
        recorder.fail(history);

        assertThat(남은_이력()).hasSize(1);
    }
}

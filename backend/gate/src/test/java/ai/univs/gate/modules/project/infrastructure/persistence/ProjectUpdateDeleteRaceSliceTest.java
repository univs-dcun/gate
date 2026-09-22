package ai.univs.gate.modules.project.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.univs.gate.modules.api_key.infrastructure.persistence.ApiKeyRepositoryImpl;
import ai.univs.gate.modules.project.application.input.UpdateProjectInput;
import ai.univs.gate.modules.project.application.usecase.DeleteProjectUseCase;
import ai.univs.gate.modules.project.application.usecase.UpdateProjectUseCase;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.jpa.JpaSliceTest;
import ai.univs.gate.support.project.ProjectService;
import jakarta.persistence.EntityManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * UG-311: 프로젝트 수정이 이미 커밋된 삭제를 되살리지 않는지 실제 DB(H2)에서 확인한다.
 *
 * <p>결함의 기전 — {@code Project} 는 낙관적 잠금(버전 컬럼)이 없고 더티 체킹 UPDATE 가 <b>전 컬럼</b>을 쓴다
 * (동적 UPDATE 애노테이션은 UG-297 가드가 금지하므로 이 전제는 유지된다). 수정 트랜잭션이 잠금 없이 행을 읽어 둔 사이 삭제가 커밋되면, 수정 커밋이
 * {@code is_deleted=false}·{@code status=ACTIVE} 를 그대로 되써 삭제가 사라진다 (UG-302 리뷰가 2스레드로 재현).
 *
 * <p>수정도 삭제와 같은 쓰기 잠금({@code validateOwnershipForUpdate})을 잡으면 두 순서 모두 안전하다.
 * <ul>
 *   <li>삭제가 먼저 커밋 → 수정은 잠금을 얻은 뒤 {@code is_deleted=false} 행을 찾지 못해 {@code PROJECT_NOT_FOUND}
 *   <li>삭제가 잠근 채 진행 중 → 수정은 삭제 커밋까지 기다린 뒤 행을 찾지 못해 역시 {@code PROJECT_NOT_FOUND}
 * </ul>
 *
 * <p>두 번째 케이스가 회귀 방지의 핵심이다. 수정이 잠금 없이 읽던 예전 코드로 되돌리면 삭제가 잠근 동안에도 곧바로
 * 읽어 버리고, UPDATE 만 잠금에 막혔다가 삭제 커밋 뒤 전 컬럼을 되써 {@code is_deleted=false} 로 되살린다 —
 * 이 테스트가 그 회귀를 잡는다 (예전 코드로 되돌려 실패를 확인함).
 *
 * <p>스레드 두 개가 각자 트랜잭션을 커밋해야 하므로 {@code @DataJpaTest} 의 기본 테스트 트랜잭션은 끈다
 * ({@code NOT_SUPPORTED}). 그래서 데이터는 테스트가 직접 지운다.
 */
@JpaSliceTest
@Import({ProjectRepositoryImpl.class, ProjectDSLRepository.class, ProjectService.class,
        UpdateProjectUseCase.class, DeleteProjectUseCase.class, ApiKeyRepositoryImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("UG-311 프로젝트 수정 vs 삭제 경쟁 (H2 슬라이스)")
class ProjectUpdateDeleteRaceSliceTest {

    private static final long OWNER = 100L;

    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private UpdateProjectUseCase updateProjectUseCase;
    @Autowired private DeleteProjectUseCase deleteProjectUseCase;

    private TransactionTemplate tx;
    private Long projectId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        projectId = tx.execute(status -> {
            Project p = Project.builder().accountId(OWNER).projectName("before").branchName("br-311")
                    .isDeleted(false).status(ProjectStatus.ACTIVE).build();
            em.persist(p);
            return p.getId();
        });
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> em.createQuery("DELETE FROM Project p WHERE p.id = :id")
                .setParameter("id", projectId).executeUpdate());
    }

    private Project reload() {
        return tx.execute(status -> em.createQuery(
                        "SELECT p FROM Project p WHERE p.id = :id", Project.class)
                .setParameter("id", projectId).getSingleResult());
    }

    @Test
    @DisplayName("삭제가 먼저 커밋되면 수정은 PROJECT_NOT_FOUND 로 끝나고 삭제 상태가 유지된다")
    void 삭제_뒤_수정은_거부된다() {
        deleteProjectUseCase.execute(OWNER, projectId);

        assertThatThrownBy(() -> updateProjectUseCase.execute(new UpdateProjectInput(OWNER, projectId, "after", null, null)))
                .isInstanceOf(CustomGateException.class)
                .extracting(e -> ((CustomGateException) e).getErrorType())
                .isEqualTo(ErrorType.PROJECT_NOT_FOUND);

        Project p = reload();
        assertThat(p.isDeleted()).isTrue();
        assertThat(p.getStatus()).isEqualTo(ProjectStatus.DELETED);
        assertThat(p.getProjectName()).isEqualTo("before");
    }

    @Test
    @DisplayName("삭제가 행을 잠근 채 진행 중일 때 들어온 수정은 삭제 커밋을 기다렸다가 PROJECT_NOT_FOUND 로 끝난다 — 되살리지 않는다")
    void 삭제_진행_중_들어온_수정은_부활시키지_않는다() throws Exception {
        CountDownLatch deleteHoldsRow = new CountDownLatch(1);
        AtomicLong updateElapsedMs = new AtomicLong(-1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Throwable> update;
        try {
            // D: 삭제와 같은 잠금을 잡고 잠시 멈춘다 — 수정이 그 사이에 들어오게 하기 위한 창.
            Future<?> delete = pool.submit(() -> tx.executeWithoutResult(status -> {
                Project p = new ProjectService(projectRepositoryImpl).validateOwnershipForUpdate(projectId, OWNER);
                deleteHoldsRow.countDown();
                sleep(400);
                p.delete();
            }));
            // U: 실제 UseCase. 잠금을 잡으면 D 의 커밋까지 막힌 뒤 is_deleted=false 행을 찾지 못한다.
            //    잠금 없이 읽던 예전 코드였다면 곧바로 읽고, UPDATE 만 D 의 잠금에 막혔다가 D 커밋 뒤 전 컬럼을
            //    되써 is_deleted=false 로 되살렸다 — 그 회귀를 이 단언이 잡는다.
            update = pool.submit(() -> {
                await(deleteHoldsRow);
                long started = System.nanoTime();
                try {
                    updateProjectUseCase.execute(new UpdateProjectInput(OWNER, projectId, "after", null, null));
                    return null;
                } catch (Throwable t) {
                    return t;
                } finally {
                    updateElapsedMs.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                }
            });
            delete.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        Throwable updateOutcome = update.get(10, TimeUnit.SECONDS);

        // 수정이 잠금 대기를 실제로 거쳤는지 — 잠금 없이 읽으면 D 의 멈춤과 무관하게 즉시 읽고 UPDATE 에서만 막힌다.
        // 어느 쪽이든 D 의 커밋(≥400ms) 뒤에 끝나야 하지만, 이 단언은 "대기 경로가 죽어 케이스 (1)로 퇴화" 를 잡는다.
        assertThat(updateElapsedMs.get()).as("수정은 삭제의 잠금 보유 창(400ms)을 기다렸어야 한다").isGreaterThanOrEqualTo(300L);

        Project p = reload();
        assertThat(p.isDeleted()).as("삭제가 수정에 덮여 사라지면 안 된다").isTrue();
        assertThat(p.getStatus()).isEqualTo(ProjectStatus.DELETED);
        assertThat(p.getProjectName()).as("삭제된 프로젝트는 고쳐지지 않는다").isEqualTo("before");
        assertThat(updateOutcome).as("수정은 거부돼야 한다").isInstanceOf(CustomGateException.class);
        assertThat(((CustomGateException) updateOutcome).getErrorType()).isEqualTo(ErrorType.PROJECT_NOT_FOUND);
    }

    @Autowired private ProjectRepositoryImpl projectRepositoryImpl;

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

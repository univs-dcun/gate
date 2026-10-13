package ai.univs.match.application.service;

import ai.univs.match.domain.entity.Branch;
import ai.univs.match.infrastructure.persistence.BranchCustomRepository;
import ai.univs.match.infrastructure.persistence.BranchRepository;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 브랜치 찾기·만들기 (UG-372, UG-374 에서 생성을 위반 없는 INSERT 로). 실제 트랜잭션·DB 동작은 {@code RegisterNewBranchRaceSliceTest} 가 본다 — 여기는 분기만.
 */
@DisplayName("BranchProvisioner")
@ExtendWith(MockitoExtension.class)
class BranchProvisionerTest {

    private static final String BRANCH_NAME = "testBranch";

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private BranchCustomRepository branchCustomRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private BranchProvisioner branchProvisioner;

    private final Branch existing = Branch.builder().id(1L).branchName(BRANCH_NAME).build();

    private static DataIntegrityViolationException 제약_위반(String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("duplicate key", new SQLException(
                        "ERROR: duplicate key value violates unique constraint \"" + constraint + "\""), constraint));
    }

    @Test
    @DisplayName("있으면 그대로 돌려주고 만들지 않는다 — 트랜잭션도 열지 않는다")
    void 있으면_그대로() {
        when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.of(existing));

        assertThat(branchProvisioner.findOrCreate(BRANCH_NAME)).isSameAs(existing);

        verifyNoInteractions(branchCustomRepository, transactionManager);
    }

    @Test
    @DisplayName("없으면 REQUIRES_NEW 트랜잭션 안에서 위반 없는 INSERT 로 만들고 커밋한 뒤, 다시 읽어 돌려준다")
    void 없으면_새_트랜잭션에서_생성() {
        when(branchRepository.findByBranchName(BRANCH_NAME))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(branchCustomRepository.insertIfAbsent(eq(BRANCH_NAME), any(LocalDateTime.class))).thenReturn(1);

        assertThat(branchProvisioner.findOrCreate(BRANCH_NAME)).isSameAs(existing);

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        InOrder order = inOrder(transactionManager, branchCustomRepository, branchRepository);
        order.verify(transactionManager).getTransaction(definition.capture());
        order.verify(branchCustomRepository).insertIfAbsent(eq(BRANCH_NAME), any(LocalDateTime.class));
        order.verify(transactionManager).commit(any());
        order.verify(branchRepository).findByBranchName(BRANCH_NAME);
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(branchRepository, never()).saveAndFlush(any());
    }

    /**
     * 다른 요청이 같은 이름을 먼저 만들고 커밋했다. PostgreSQL {@code ON CONFLICT DO NOTHING} 은 위반 대신 0 건으로 끝난다 —
     * Hibernate 가 ERROR 줄을 남기지 않는다. 생성 트랜잭션은 커밋(아무것도 안 함)하고 다시 읽은 브랜치로 이어 간다.
     */
    @Test
    @DisplayName("경합에서 지면(0 건) 위반 없이 다시 읽어 돌려준다")
    void 경합에서_지면_0건_다시_읽기() {
        when(branchRepository.findByBranchName(BRANCH_NAME))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(branchCustomRepository.insertIfAbsent(eq(BRANCH_NAME), any(LocalDateTime.class))).thenReturn(0);

        assertThat(branchProvisioner.findOrCreate(BRANCH_NAME)).isSameAs(existing);

        verify(transactionManager).commit(any());
        verify(transactionManager, never()).rollback(any());
    }

    /** 위반 없는 INSERT 인데도 위반이 나면(예외 대비) — UG-372 의 "롤백 뒤 다시 읽기" 경로가 그대로 받는다. */
    @Test
    @DisplayName("그래도 브랜치 이름 유니크 위반이 나면 생성 트랜잭션을 롤백한 뒤 다시 읽어 돌려준다")
    void 위반이면_롤백_뒤_다시_읽기() {
        when(branchRepository.findByBranchName(BRANCH_NAME))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(branchCustomRepository.insertIfAbsent(eq(BRANCH_NAME), any(LocalDateTime.class)))
                .thenThrow(제약_위반("uk_branch_branch_name"));

        assertThat(branchProvisioner.findOrCreate(BRANCH_NAME)).isSameAs(existing);

        InOrder order = inOrder(transactionManager, branchCustomRepository, branchRepository);
        order.verify(branchCustomRepository).insertIfAbsent(eq(BRANCH_NAME), any(LocalDateTime.class));
        order.verify(transactionManager).rollback(any());
        order.verify(branchRepository).findByBranchName(BRANCH_NAME);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    @DisplayName("만들었는데(또는 졌는데) 다시 읽어도 없으면 INTERNAL_SERVER_ERROR (지우는 경로가 없어 일어나지 않아야 한다)")
    void 다시_읽어도_없으면_서버_오류() {
        when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.empty());
        when(branchCustomRepository.insertIfAbsent(eq(BRANCH_NAME), any(LocalDateTime.class))).thenReturn(0);

        assertThatThrownBy(() -> branchProvisioner.findOrCreate(BRANCH_NAME))
                .isInstanceOf(CustomFaceMatcherException.class)
                .extracting(ex -> ((CustomFaceMatcherException) ex).getErrorType())
                .isEqualTo(ErrorType.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("브랜치 저장의 다른 위반은 바꾸지 않고 그대로 던진다")
    void 다른_위반은_그대로() {
        DataIntegrityViolationException other = 제약_위반("branch_name_not_null");
        when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.empty());
        when(branchCustomRepository.insertIfAbsent(eq(BRANCH_NAME), any(LocalDateTime.class))).thenThrow(other);

        assertThatThrownBy(() -> branchProvisioner.findOrCreate(BRANCH_NAME)).isSameAs(other);
    }

    /** 트랜잭션 안에서 부르면 커넥션을 둘 쥔다 — 풀 교착 구조로 되돌아가는 것을 막는다. */
    @Test
    @DisplayName("트랜잭션 안에서 불리면 DB 를 건드리기 전에 IllegalStateException")
    void 트랜잭션_안에서는_거부() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> branchProvisioner.findOrCreate(BRANCH_NAME))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("UG-372");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(branchRepository, branchCustomRepository, transactionManager);
    }
}

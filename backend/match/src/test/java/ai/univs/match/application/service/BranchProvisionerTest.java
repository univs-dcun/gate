package ai.univs.match.application.service;

import ai.univs.match.domain.entity.Branch;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 브랜치 찾기·만들기 (UG-372). 실제 트랜잭션·DB 동작은 {@code RegisterNewBranchRaceSliceTest} 가 본다 — 여기는 분기만.
 */
@DisplayName("BranchProvisioner")
@ExtendWith(MockitoExtension.class)
class BranchProvisionerTest {

    private static final String BRANCH_NAME = "testBranch";

    @Mock
    private BranchRepository branchRepository;

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

        verify(branchRepository, never()).saveAndFlush(any());
        verifyNoInteractions(transactionManager);
    }

    @Test
    @DisplayName("없으면 REQUIRES_NEW 트랜잭션 안에서 만들고 커밋한다")
    void 없으면_새_트랜잭션에서_생성() {
        when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.empty());
        when(branchRepository.saveAndFlush(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

        Branch created = branchProvisioner.findOrCreate(BRANCH_NAME);

        assertThat(created.getBranchName()).isEqualTo(BRANCH_NAME);
        assertThat(created.getCreatedAt()).isNotNull();
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        InOrder order = inOrder(transactionManager, branchRepository);
        order.verify(transactionManager).getTransaction(definition.capture());
        order.verify(branchRepository).saveAndFlush(any(Branch.class));
        order.verify(transactionManager).commit(any());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 다른 요청이 같은 이름을 먼저 만들고 커밋했다. 생성 트랜잭션은 롤백되고, 그 밖에서 다시 읽은 브랜치로 이어 간다.
     * UG-340 에서는 여기서 INTERNAL_SERVER_ERROR 였다.
     */
    @Test
    @DisplayName("브랜치 이름 유니크 위반이면 생성 트랜잭션을 롤백한 뒤 다시 읽어 돌려준다")
    void 경합에서_지면_다시_읽기() {
        when(branchRepository.findByBranchName(BRANCH_NAME))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(branchRepository.saveAndFlush(any(Branch.class))).thenThrow(제약_위반("uk_branch_branch_name"));

        assertThat(branchProvisioner.findOrCreate(BRANCH_NAME)).isSameAs(existing);

        InOrder order = inOrder(transactionManager, branchRepository);
        order.verify(branchRepository).saveAndFlush(any(Branch.class));
        order.verify(transactionManager).rollback(any());
        order.verify(branchRepository).findByBranchName(BRANCH_NAME);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    @DisplayName("위반을 받았는데 다시 읽어도 없으면 INTERNAL_SERVER_ERROR (지우는 경로가 없어 일어나지 않아야 한다)")
    void 다시_읽어도_없으면_서버_오류() {
        when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.empty());
        when(branchRepository.saveAndFlush(any(Branch.class))).thenThrow(제약_위반("uk_branch_branch_name"));

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
        when(branchRepository.saveAndFlush(any(Branch.class))).thenThrow(other);

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
        verifyNoInteractions(branchRepository, transactionManager);
    }
}

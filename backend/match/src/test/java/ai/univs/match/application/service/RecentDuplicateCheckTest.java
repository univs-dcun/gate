package ai.univs.match.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.univs.match.application.enums.DescriptorSpec;
import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.infrastructure.persistence.DescriptorCustomRepository;
import ai.univs.match.infrastructure.persistence.DescriptorRepository;
import ai.univs.match.infrastructure.persistence.projection.MatchPostgresqlProjection;
import ai.univs.match.infrastructure.persistence.projection.MatchResultProjection;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 같은 얼굴 동시 등록의 커밋 뒤 재검사 (UG-374). 분기만 본다 — 실제 DB 의 잠금·경합은 {@code SameFaceConcurrentRegisterRealDbTest}.
 */
@DisplayName("UG-374: RecentDuplicateCheck")
@ExtendWith(MockitoExtension.class)
class RecentDuplicateCheckTest {

    private static final Long BRANCH_ID = 3L;
    private static final Long SELF_ID = 900L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 0, 0);

    @Mock private DescriptorCustomRepository descriptorCustomRepository;
    @Mock private DescriptorRepository descriptorRepository;
    /** 목이라 실제 트랜잭션은 없다 — 템플릿이 getTransaction·commit·rollback 을 부르는 순서만 본다. */
    @Mock private PlatformTransactionManager transactionManager;

    private RecentDuplicateCheck check;
    private DescriptorDetail detail;

    @BeforeEach
    void setUp() {
        check = new RecentDuplicateCheck(descriptorCustomRepository, descriptorRepository, transactionManager);
        check.now = () -> NOW;
        byte[] bytes = new byte[520];
        bytes[4] = (byte) 59;
        detail = DescriptorDetail.from(Base64.getEncoder().encodeToString(bytes));
    }

    private static Optional<MatchResultProjection> 가장_가까운(double distance) {
        return Optional.of(new MatchResultProjection(new MatchPostgresqlProjection("other-face", distance)));
    }

    /** 유사도 → 거리 (Platt scaling 역변환). 테스트가 임계치 근처를 직접 고르려고 둔다. */
    private static double 거리(double similarity) {
        DescriptorSpec spec = DescriptorSpec.VERSION_59;
        return (Math.log(1 / similarity - 1) - spec.getDescriptorPlattSacleingB()) / spec.getDescriptorPlattSacleingA();
    }

    private void 최근_조회가_돌려준다(Optional<MatchResultProjection> result) {
        when(descriptorCustomRepository.nearestRecent(anyLong(), anyLong(), any(), anyInt(), any(), anyInt()))
                .thenReturn(result);
    }

    @Test
    @DisplayName("한 트랜잭션 안에서 브랜치를 먼저 잠그고, 그다음 최근 특징점과 비교한다")
    void 잠금_먼저() {
        최근_조회가_돌려준다(Optional.empty());

        check.withdrawIfDuplicated(BRANCH_ID, SELF_ID, detail);

        InOrder order = inOrder(transactionManager, descriptorCustomRepository);
        order.verify(transactionManager).getTransaction(any());
        order.verify(descriptorCustomRepository).lockBranchForRecheck(BRANCH_ID);
        order.verify(descriptorCustomRepository).nearestRecent(anyLong(), anyLong(), any(), anyInt(), any(), anyInt());
        order.verify(transactionManager).commit(any());
    }

    @Test
    @DisplayName("같은 브랜치·자기 제외·같은 버전·최근 60초로 조회한다")
    void 조회_조건() {
        최근_조회가_돌려준다(Optional.empty());

        check.withdrawIfDuplicated(BRANCH_ID, SELF_ID, detail);

        verify(descriptorCustomRepository).nearestRecent(
                eq(BRANCH_ID), eq(SELF_ID), eq(NOW.minusSeconds(60)), eq(59), eq(detail.descriptorBody()), eq(512));
    }

    @Test
    @DisplayName("같은 얼굴(임계치 이상)이 보이면 자기 행을 지우고 true — 호출자는 MATCH-003")
    void 같은_얼굴이면_물린다() {
        최근_조회가_돌려준다(가장_가까운(0.0));

        assertThat(check.withdrawIfDuplicated(BRANCH_ID, SELF_ID, detail)).isTrue();

        verify(descriptorRepository).deleteById(SELF_ID);
        verify(transactionManager).commit(any());
    }

    @Test
    @DisplayName("임계치 바로 위(0.86)면 물린다")
    void 임계치_위() {
        최근_조회가_돌려준다(가장_가까운(거리(0.86)));

        assertThat(check.withdrawIfDuplicated(BRANCH_ID, SELF_ID, detail)).isTrue();
    }

    @Test
    @DisplayName("임계치 바로 아래(0.84)면 남긴다 — 다른 사람이다")
    void 임계치_아래() {
        최근_조회가_돌려준다(가장_가까운(거리(0.84)));

        assertThat(check.withdrawIfDuplicated(BRANCH_ID, SELF_ID, detail)).isFalse();

        verify(descriptorRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("최근 특징점이 없으면 남긴다")
    void 최근_없음() {
        최근_조회가_돌려준다(Optional.empty());

        assertThat(check.withdrawIfDuplicated(BRANCH_ID, SELF_ID, detail)).isFalse();

        verify(descriptorRepository, never()).deleteById(any());
    }

    /**
     * 재검사를 못 끝냈다고 실패로 돌려주면 match 에는 행이 남았는데 gate 는 실패로 기록한다 — match 에만 있는 행이 생긴다.
     * 등록을 유지한다(UG-374 이전과 같은 결과).
     */
    @Test
    @DisplayName("잠금을 못 얻으면(대기 상한 등) 던지지 않고 남긴다 — 등록 유지")
    void 잠금_실패면_유지() {
        doThrow(new CannotAcquireLockException("lock timeout"))
                .when(descriptorCustomRepository).lockBranchForRecheck(BRANCH_ID);
        when(descriptorRepository.existsById(SELF_ID)).thenReturn(true);

        assertThat(check.withdrawIfDuplicated(BRANCH_ID, SELF_ID, detail)).isFalse();

        verify(descriptorRepository, never()).deleteById(any());
        verify(transactionManager).rollback(any());
    }

    @Test
    @DisplayName("자기 행을 지우다 실패하면 롤백하고 남긴다 — 행이 남았으니 성공으로 돌려줘야 gate 와 맞는다")
    void 삭제_실패면_유지() {
        최근_조회가_돌려준다(가장_가까운(0.0));
        doThrow(new DataAccessResourceFailureException("connection lost"))
                .when(descriptorRepository).deleteById(SELF_ID);
        when(descriptorRepository.existsById(SELF_ID)).thenReturn(true);

        assertThat(check.withdrawIfDuplicated(BRANCH_ID, SELF_ID, detail)).isFalse();

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    /** 반박 리뷰 L2 — 삭제 커밋 중 연결이 끊기면 지워졌는지 모른다. 지워졌는데 성공으로 돌려주면 gate 에만 faceId 가 남는다. */
    @Test
    @DisplayName("재검사가 실패했어도 자기 행이 이미 없으면 물린 것으로 본다(true) — 커밋 결과를 모르는 삭제")
    void 실패했지만_이미_지워졌으면_물림() {
        최근_조회가_돌려준다(가장_가까운(0.0));
        doThrow(new DataAccessResourceFailureException("commit outcome unknown"))
                .when(transactionManager).commit(any());
        when(descriptorRepository.existsById(SELF_ID)).thenReturn(false);

        assertThat(check.withdrawIfDuplicated(BRANCH_ID, SELF_ID, detail)).isTrue();
    }

    @Test
    @DisplayName("재검사가 실패하고 다시 읽기도 실패하면 유지한다(false)")
    void 다시_읽기도_실패하면_유지() {
        doThrow(new CannotAcquireLockException("lock timeout"))
                .when(descriptorCustomRepository).lockBranchForRecheck(BRANCH_ID);
        when(descriptorRepository.existsById(SELF_ID)).thenThrow(new DataAccessResourceFailureException("down"));

        assertThat(check.withdrawIfDuplicated(BRANCH_ID, SELF_ID, detail)).isFalse();
    }
}

package ai.univs.gate.support.reconcile;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.support.jpa.JpaSliceTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 결과를 모르는 등록을 고르는 쿼리 (UG-338).
 *
 * <p>고른 행의 발급 id 로 하위 서비스에서 <b>특징점을 지운다</b>. 조건이 어긋나면 살아 있는 사용자의
 * 템플릿을 지우거나(성공 행·최근 행을 집으면), 고아를 영영 못 찾는다(결과를 모르는 행을 놓치면).
 * 되돌릴 수 없으므로 실제 DB 에서 확인한다.
 *
 * <p>{@code created_at} 은 {@code @CreatedDate} 가 채우고 {@code updatable = false} 라 네이티브 UPDATE
 * 로 과거 시각을 만든다.
 */
@JpaSliceTest
@Import(OrphanRegistrationRepository.class)
@DisplayName("UG-338: 결과를 모르는 등록 선별 쿼리")
class OrphanRegistrationRepositorySliceTest {

    @Autowired private OrphanRegistrationRepository repository;
    @Autowired private EntityManager em;

    private Project 프로젝트;

    @BeforeEach
    void setUp() {
        프로젝트 = Project.builder().accountId(9L).projectName("p").branchName("branch-reconcile")
                .isDeleted(false).status(ProjectStatus.ACTIVE).build();
        em.persist(프로젝트);
    }

    private static LocalDateTime 분전(long minutes) {
        return LocalDateTime.now(ZoneOffset.UTC).minusMinutes(minutes);
    }

    private FeatureHistory 등록행(long 분, String issuedId) {
        FeatureHistory h = FeatureHistory.register(프로젝트, FeatureType.FACE, false, null,
                UUID.randomUUID().toString(), true, issuedId);
        em.persist(h);
        return h;
    }

    private Long 저장(FeatureHistory h, long 분) {
        em.flush();
        em.createNativeQuery("UPDATE feature_history SET created_at = :t WHERE feature_history_id = :id")
                .setParameter("t", 분전(분)).setParameter("id", h.getId()).executeUpdate();
        em.clear();
        return h.getId();
    }

    private List<Long> 고른_것() {
        return repository.findStaleRegistrations(분전(10), 분전(24 * 60), 0L, 50).stream()
                .map(FeatureHistory::getId).toList();
    }

    @Test
    @DisplayName("시작 상태로 멈춘 등록(발급 id 있음, 10분~24시간 전)을 고른다")
    void 시작_상태를_고른다() {
        Long id = 저장(등록행(30, "a0000000-0000-0000-0000-000000000001"), 30);

        assertThat(고른_것()).containsExactly(id);
    }

    /** 읽기 타임아웃은 하위가 등록을 끝냈는데 응답만 늦은 경우일 수 있다 — 결과를 모른다. */
    @Test
    @DisplayName("응답 없음으로 실패한 등록도 고른다 — 결과를 모른다")
    void 응답_없음_실패도_고른다() {
        FeatureHistory h = 등록행(30, "a0000000-0000-0000-0000-000000000002");
        h.failUpstream(new RemoteCallException(RemoteCallException.NO_RESPONSE, "face.createFace", null));
        Long id = 저장(h, 30);

        assertThat(고른_것()).containsExactly(id);
    }

    @Test
    @DisplayName("하위가 거절한 등록(확정 실패)은 고르지 않는다 — id 가 지워져 있다")
    void 확정_실패는_고르지_않는다() {
        FeatureHistory h = 등록행(30, "a0000000-0000-0000-0000-000000000003");
        h.fail("FACE_NOT_FOUND");
        저장(h, 30);

        assertThat(고른_것()).isEmpty();
    }

    /** 성공 행을 고르면 살아 있는 사용자의 템플릿을 지운다. */
    @Test
    @DisplayName("성공한 등록은 고르지 않는다")
    void 성공은_고르지_않는다() {
        FeatureHistory h = 등록행(30, "a0000000-0000-0000-0000-000000000004");
        BiometricFeature f = BiometricFeature.builder().project(프로젝트).type(FeatureType.FACE)
                .featureId("a0000000-0000-0000-0000-000000000004").isDeleted(false).build();
        em.persist(f);
        h.successRegister(f);
        저장(h, 30);

        assertThat(고른_것()).isEmpty();
    }

    /** 10분이 안 된 행은 아직 처리 중일 수 있다 — 되돌리면 막 성공할 요청을 망친다. */
    @Test
    @DisplayName("10분이 안 된 행은 고르지 않는다")
    void 최근은_고르지_않는다() {
        저장(등록행(3, "a0000000-0000-0000-0000-000000000005"), 3);

        assertThat(고른_것()).isEmpty();
    }

    @Test
    @DisplayName("24시간이 지난 행은 다시 고르지 않는다")
    void 오래된_것은_고르지_않는다() {
        저장(등록행(25 * 60, "a0000000-0000-0000-0000-000000000006"), 25 * 60);

        assertThat(고른_것()).isEmpty();
    }

    /** UG-338 이전의 시작 행은 id 가 없다 — 무엇을 지울지 모른다. */
    @Test
    @DisplayName("발급 id 가 없는 행은 고르지 않는다")
    void id_없는_행은_고르지_않는다() {
        저장(등록행(30, null), 30);

        assertThat(고른_것()).isEmpty();
    }

    /** 삭제 행을 여기서 밀어붙이면 오래전 요청을 지금 집행하게 된다 — 삭제 유스케이스가 재시도로 수렴시킨다. */
    @Test
    @DisplayName("삭제 행은 고르지 않는다")
    void 삭제_행은_고르지_않는다() {
        BiometricFeature f = BiometricFeature.builder().project(프로젝트).type(FeatureType.FACE)
                .featureId("a0000000-0000-0000-0000-000000000007").isDeleted(false).build();
        em.persist(f);
        FeatureHistory h = FeatureHistory.delete(프로젝트, f, UUID.randomUUID().toString());
        em.persist(h);
        저장(h, 30);

        assertThat(고른_것()).isEmpty();
    }

    @Test
    @DisplayName("고른 행은 프로젝트를 함께 읽어 온다 — 트랜잭션 밖에서 브랜치 이름을 읽는다")
    void 프로젝트를_함께_읽는다() {
        저장(등록행(30, "a0000000-0000-0000-0000-000000000008"), 30);

        FeatureHistory 고른 = repository.findStaleRegistrations(분전(10), 분전(24 * 60), 0L, 50).get(0);
        em.clear();
        assertThat(고른.getProject().getBranchName()).isEqualTo("branch-reconcile");
    }

    @Test
    @DisplayName("featureExists — 소프트 삭제된 특징점까지 있다고 본다")
    void 특징점_존재() {
        em.persist(BiometricFeature.builder().project(프로젝트).type(FeatureType.FACE)
                .featureId("a0000000-0000-0000-0000-000000000009").isDeleted(true).build());
        em.flush();

        assertThat(repository.featureExists("a0000000-0000-0000-0000-000000000009")).isTrue();
        assertThat(repository.featureExists("a0000000-0000-0000-0000-000000000099")).isFalse();
    }

    /** 안전장치는 넓을수록 안전하다 — 다른 프로젝트·방식에 있어도 있다고 본다 (UG-338 반박 리뷰). */
    @Test
    @DisplayName("featureExists — 프로젝트·방식으로 좁히지 않는다")
    void 특징점_존재_범위() {
        Project 다른 = Project.builder().accountId(8L).projectName("other").branchName("branch-other")
                .isDeleted(false).status(ProjectStatus.ACTIVE).build();
        em.persist(다른);
        em.persist(BiometricFeature.builder().project(다른).type(FeatureType.PALM)
                .featureId("a0000000-0000-0000-0000-000000000010").isDeleted(false).build());
        em.flush();

        assertThat(repository.featureExists("a0000000-0000-0000-0000-000000000010")).isTrue();
    }

    @Test
    @DisplayName("상한만큼만, id 순으로, 커서 뒤의 것만 고른다")
    void 상한과_커서() {
        Long a = 저장(등록행(30, "a0000000-0000-0000-0000-000000000011"), 30);
        Long b = 저장(등록행(30, "a0000000-0000-0000-0000-000000000012"), 40);
        Long c = 저장(등록행(30, "a0000000-0000-0000-0000-000000000013"), 50);

        assertThat(repository.findStaleRegistrations(분전(10), 분전(24 * 60), 0L, 2).stream()
                .map(FeatureHistory::getId).toList())
                .as("created_at 이 아니라 id 순 — 커서가 id 다").containsExactly(a, b);
        assertThat(repository.findStaleRegistrations(분전(10), 분전(24 * 60), b, 2).stream()
                .map(FeatureHistory::getId).toList()).containsExactly(c);
    }
}

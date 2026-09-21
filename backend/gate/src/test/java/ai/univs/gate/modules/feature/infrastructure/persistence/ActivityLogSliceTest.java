package ai.univs.gate.modules.feature.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.modules.feature.domain.entity.ActivityLog;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.ActivitySource;
import ai.univs.gate.modules.feature.domain.enums.ActivityType;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.domain.repository.ActivityLogRepository;
import ai.univs.gate.modules.feature.infrastructure.persistence.query.MatchHistoryQuery;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.support.jpa.JpaSliceTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

/**
 * UG-326: match_history ∪ feature_history 를 한 목록으로 읽는 실제 SQL.
 *
 * <p>{@code ActivityLog} 는 {@code @Subselect} 라 이 테스트가 UNION SQL 자체를 H2 에 태운다 — 컬럼 수·타입
 * 정합, {@code REGISTER} 제외 필터, 교차 정렬, 페이징이 전부 여기서 결정된다. Mockito 로는 이 중 어느
 * 것도 검증할 수 없다.
 *
 * <p>사건 시각은 저장 뒤 JPQL 로 덮어쓴다 ({@code @CreatedDate} 가 저장 시점에 채우기 때문).
 */
@JpaSliceTest
@DisplayName("UG-326: 통합 이력 목록 (match_history ∪ feature_history)")
class ActivityLogSliceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 1, 9, 0);

    @Autowired private EntityManager em;
    /** Impl 을 거친다 — 위임 계층까지 한 번에 검증 (PIT 가 위임 메서드의 null 반환 뮤턴트를 생존시켰다). */
    private ActivityLogRepository repo;
    private Project project;
    private Project other;

    @BeforeEach
    void setUp() {
        repo = new ActivityLogRepositoryImpl(new ActivityLogDSLRepository(em));
        project = 프로젝트("branch-1");
        other = 프로젝트("branch-2");
    }

    // ── 픽스처 ──────────────────────────────────────────────────────────────────

    private Project 프로젝트(String branch) {
        Project p = Project.builder().accountId(100L).projectName("t").branchName(branch)
                .isDeleted(false).status(ProjectStatus.ACTIVE).build();
        em.persist(p); return p;
    }

    /** 인증 시도 행. minutes 는 T0 기준 분 오프셋 — 교차 정렬을 검증하기 위해 두 테이블에 번갈아 준다. */
    private MatchHistory 인증(Project p, MatchType type, FeatureType ft, boolean success, String memo, int minutes) {
        MatchHistory m = MatchHistory.builder()
                .project(p).featureType(ft).matchType(type)
                .matchTime(T0.plusMinutes(minutes)).checkLiveness(false).success(success)
                .userDescription(memo).featureId("fid-" + memo).similarity(new BigDecimal("88.50"))
                .transactionUuid(UUID.randomUUID().toString()).build();
        em.persist(m); 시각(m.getClass().getSimpleName(), m.getId(), T0.plusMinutes(minutes)); return m;
    }

    private FeatureHistory 등록(Project p, FeatureType ft, String memo, int minutes) {
        BiometricFeature f = BiometricFeature.builder().project(p).type(ft).featureId("fid-" + memo)
                .description(memo).isDeleted(false).transactionUuid(UUID.randomUUID().toString()).build();
        em.persist(f);
        FeatureHistory h = FeatureHistory.register(p, ft, false, null, UUID.randomUUID().toString(), true);
        h.successRegister(f); em.persist(h);
        시각("FeatureHistory", h.getId(), T0.plusMinutes(minutes)); return h;
    }

    private FeatureHistory 삭제(Project p, FeatureHistory 등록된, boolean success, int minutes) {
        BiometricFeature f = em.find(BiometricFeature.class, 등록된.getFeatureSeq());
        FeatureHistory h = FeatureHistory.delete(p, f, UUID.randomUUID().toString());
        if (success) h.successDelete(); else h.fail("INTERNAL_SERVER_ERROR");
        em.persist(h); 시각("FeatureHistory", h.getId(), T0.plusMinutes(minutes)); return h;
    }

    private void 시각(String entity, Long id, LocalDateTime at) {
        em.flush();
        em.createQuery("UPDATE " + entity + " e SET e.createdAt = :at, e.updatedAt = :at WHERE e.id = :id")
                .setParameter("at", at).setParameter("id", id).executeUpdate();
        em.clear();
    }

    private static MatchHistoryQuery 조회(String matchType, boolean includeDeletions) {
        return 조회(matchType, "ALL", "ALL", null, 1, 100, includeDeletions);
    }

    private static MatchHistoryQuery 조회(String matchType, String featureType, String result, String keyword,
                                        int page, int size, boolean includeDeletions) {
        return new MatchHistoryQuery(1L, "key", keyword, matchType, featureType, result, page, size,
                false, null, null, "DESC", "identifyTime", includeDeletions);
    }

    private List<ActivityType> 타입들(Page<ActivityLog> page) {
        return page.getContent().stream().map(ActivityLog::getActivityType).toList();
    }

    // ── 본문 ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("전체(ALL): 두 테이블이 시간순으로 섞여 나오고, includeDeletions 없이는 DELETE 만 빠진다")
    void 전체는_섞여_나오고_삭제는_기본_숨김() {
        FeatureHistory reg = 등록(project, FeatureType.FACE, "홍길동", 0);         // 09:00
        인증(project, MatchType.IDENTIFY, FeatureType.FACE, true, "홍길동", 10);     // 09:10
        인증(project, MatchType.VERIFY_ID, FeatureType.FACE, false, "김철수", 20);   // 09:20
        삭제(project, reg, true, 30);                                              // 09:30
        인증(project, MatchType.LIVENESS, FeatureType.FACE, true, "홍길동", 40);     // 09:40

        Page<ActivityLog> page = repo.findAllByQuery(조회("ALL", false), project.getId());

        assertThat(타입들(page))
                .as("최신순, 두 출처 교차, DELETE 제외")
                .containsExactly(ActivityType.LIVENESS, ActivityType.VERIFY_ID, ActivityType.IDENTIFY, ActivityType.REGISTER);
        assertThat(page.getTotalElements()).isEqualTo(4);

        Page<ActivityLog> with = repo.findAllByQuery(조회("ALL", true), project.getId());
        assertThat(타입들(with)).containsExactly(
                ActivityType.LIVENESS, ActivityType.DELETE, ActivityType.VERIFY_ID, ActivityType.IDENTIFY, ActivityType.REGISTER);
        assertThat(with.getContent().get(1).getSource()).isEqualTo(ActivitySource.FEATURE);
        assertThat(with.getContent().get(2).getSource()).isEqualTo(ActivitySource.MATCH);
    }

    @Test
    @DisplayName("UG-328: 일련번호(id)는 두 출처에 걸쳐 하나의 시퀀스 — 유일하고, 목록 순서(최신순)와 단조 일치한다")
    void 일련번호는_공유_시퀀스() {
        // 인증 → 특징점 → 인증 → 특징점 순으로 번갈아 넣어 두 테이블의 PK 가 각자 오르는데도 id 는 하나의 축인지 본다.
        인증(project, MatchType.IDENTIFY, FeatureType.FACE, true, "a", 0);
        FeatureHistory reg = 등록(project, FeatureType.FACE, "a", 1);
        인증(project, MatchType.VERIFY_ID, FeatureType.FACE, true, "a", 2);
        삭제(project, reg, true, 3);
        인증(project, MatchType.LIVENESS, FeatureType.FACE, true, "a", 4);

        Page<ActivityLog> page = repo.findAllByQuery(조회("ALL", true), project.getId());
        List<Long> ids = page.getContent().stream().map(ActivityLog::getId).toList();

        assertThat(ids).hasSize(5).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(ids).as("최신순 목록이면 id 도 내림차순 — 시퀀스가 시간과 단조 일치").isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(타입들(page)).containsExactly(ActivityType.LIVENESS, ActivityType.DELETE, ActivityType.VERIFY_ID, ActivityType.REGISTER, ActivityType.IDENTIFY);
        // 원 테이블 PK(sourceId) 는 두 출처가 서로 겹칠 수 있다 — 실측에서 [12, 11, 11, 10, 10] 처럼 나왔고,
        // 그게 이 시퀀스를 둔 이유다. 값 자체는 H2 IDENTITY 가 컨텍스트 안에서 이어져 환경 의존이라 단언하지 않는다.
    }

    @Test
    @DisplayName("matchType=DELETE 를 명시하면 includeDeletions 와 무관하게 삭제 행이 나온다")
    void 삭제를_명시하면_나온다() {
        FeatureHistory reg = 등록(project, FeatureType.FACE, "a", 0);
        삭제(project, reg, true, 5);
        인증(project, MatchType.IDENTIFY, FeatureType.FACE, true, "a", 10);

        Page<ActivityLog> page = repo.findAllByQuery(조회("DELETE", false), project.getId());
        assertThat(타입들(page)).containsExactly(ActivityType.DELETE);
        ActivityLog del = page.getContent().get(0);
        assertThat(del.getSource()).isEqualTo(ActivitySource.FEATURE);
        assertThat(del.getSimilarity()).as("특징점 사건에는 유사도가 없다").isNull();
        assertThat(del.getMatchedFeatureImagePath()).isNull();
        assertThat(del.getFeatureSeq()).isEqualTo(reg.getFeatureSeq());
        assertThat(del.getUserDescription()).as("삭제 시점 스냅샷").isEqualTo("a");
    }

    @Test
    @DisplayName("match_history 에 REGISTER 행이 남아 있어도 목록에 두 번 나오지 않는다")
    void match_history_의_REGISTER_는_읽지_않는다() {
        등록(project, FeatureType.FACE, "x", 0);
        // V27 이 놓쳤다고 가정한 잔존 행
        인증(project, MatchType.REGISTER, FeatureType.FACE, true, "x", 1);

        Page<ActivityLog> page = repo.findAllByQuery(조회("REGISTER", false), project.getId());
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getSource()).isEqualTo(ActivitySource.FEATURE);
    }

    @Test
    @DisplayName("검색어는 두 출처의 메모·Feature ID·요청 ID 를 함께 훑는다")
    void 검색어는_양쪽을_훑는다() {
        등록(project, FeatureType.FACE, "홍길동", 0);
        인증(project, MatchType.IDENTIFY, FeatureType.FACE, true, "홍길동", 1);
        인증(project, MatchType.IDENTIFY, FeatureType.FACE, true, "김철수", 2);

        Page<ActivityLog> page = repo.findAllByQuery(조회("ALL", "ALL", "ALL", "홍길", 1, 10, false), project.getId());
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(ActivityLog::getSource)
                .containsExactlyInAnyOrder(ActivitySource.MATCH, ActivitySource.FEATURE);
    }

    @Test
    @DisplayName("인증 방식·결과 필터는 두 출처에 같이 걸린다")
    void 인증방식과_결과_필터() {
        FeatureHistory palmReg = 등록(project, FeatureType.PALM, "p", 0);
        삭제(project, palmReg, false, 1);                                         // PALM, 실패
        등록(project, FeatureType.FACE, "f", 2);
        인증(project, MatchType.IDENTIFY, FeatureType.PALM, false, "p", 3);
        인증(project, MatchType.IDENTIFY, FeatureType.FACE, true, "f", 4);

        Page<ActivityLog> palmFail = repo.findAllByQuery(조회("ALL", "PALM", "FAILURE", null, 1, 10, true), project.getId());
        assertThat(타입들(palmFail)).containsExactly(ActivityType.IDENTIFY, ActivityType.DELETE);

        Page<ActivityLog> faceOk = repo.findAllByQuery(조회("ALL", "FACE", "SUCCESS", null, 1, 10, true), project.getId());
        assertThat(타입들(faceOk)).containsExactly(ActivityType.IDENTIFY, ActivityType.REGISTER);
    }

    @Test
    @DisplayName("페이징은 UNION 전체를 한 번에 자른다 — 출처별로 잘라 합치지 않는다")
    void 페이징은_전체_기준() {
        // 인증 3건이 특징점 사건 2건보다 전부 최신. 출처별 2건씩 받아 합치는 방식이면 2페이지에 REGISTER 가 못 나온다.
        FeatureHistory reg = 등록(project, FeatureType.FACE, "a", 0);
        삭제(project, reg, true, 1);
        인증(project, MatchType.IDENTIFY, FeatureType.FACE, true, "a", 10);
        인증(project, MatchType.IDENTIFY, FeatureType.FACE, true, "a", 11);
        인증(project, MatchType.IDENTIFY, FeatureType.FACE, true, "a", 12);

        Page<ActivityLog> p1 = repo.findAllByQuery(조회("ALL", "ALL", "ALL", null, 1, 2, true), project.getId());
        Page<ActivityLog> p2 = repo.findAllByQuery(조회("ALL", "ALL", "ALL", null, 2, 2, true), project.getId());
        Page<ActivityLog> p3 = repo.findAllByQuery(조회("ALL", "ALL", "ALL", null, 3, 2, true), project.getId());

        assertThat(p1.getTotalElements()).isEqualTo(5);
        assertThat(타입들(p1)).containsExactly(ActivityType.IDENTIFY, ActivityType.IDENTIFY);
        assertThat(타입들(p2)).containsExactly(ActivityType.IDENTIFY, ActivityType.DELETE);
        assertThat(타입들(p3)).containsExactly(ActivityType.REGISTER);
    }

    @Test
    @DisplayName("프로젝트 경계와 전체 건수")
    void 프로젝트_경계와_전체_수() {
        FeatureHistory reg = 등록(project, FeatureType.FACE, "a", 0);
        삭제(project, reg, true, 1);
        인증(project, MatchType.IDENTIFY, FeatureType.FACE, true, "a", 2);
        등록(other, FeatureType.FACE, "z", 3);
        인증(other, MatchType.IDENTIFY, FeatureType.FACE, true, "z", 4);

        assertThat(repo.countByProjectId(project.getId(), false)).as("삭제 제외").isEqualTo(2);
        assertThat(repo.countByProjectId(project.getId(), true)).isEqualTo(3);
        assertThat(repo.findAllByQuery(조회("ALL", true), other.getId()).getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("트랜잭션 UUID 단건 조회는 특징점 사건도 찾는다")
    void uuid_단건은_특징점_사건도_찾는다() {
        FeatureHistory reg = 등록(project, FeatureType.FACE, "a", 0);
        FeatureHistory del = 삭제(project, reg, true, 1);

        assertThat(repo.findLatestByProjectIdAndTransactionUuid(project.getId(), del.getTransactionUuid()))
                .isPresent().get()
                .satisfies(l -> {
                    assertThat(l.getActivityType()).isEqualTo(ActivityType.DELETE);
                    assertThat(l.getSourceId()).isEqualTo(del.getId());
                    assertThat(l.getId()).as("UG-328: id 는 공유 시퀀스").isNotNull()
                            .isEqualTo(em.find(FeatureHistory.class, del.getId()).getActivitySeq());
                });
        assertThat(repo.findLatestByProjectIdAndTransactionUuid(other.getId(), del.getTransactionUuid()))
                .as("다른 프로젝트에서는 보이지 않는다").isEmpty();
    }
}

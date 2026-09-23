package ai.univs.gate.support.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureActionType;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.support.jpa.JpaSliceTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 정리 대상을 고르는 쿼리 (UG-282).
 *
 * <p>고른 행은 <b>물리 삭제</b>되고 그중 일부는 파일까지 함께 사라진다. 조건이 어긋나면 아직
 * 보존 기간이 남은 이력을, 그것도 법정 보존 의무가 걸렸을 수 있는 이력을 지운다. 되돌릴 수
 * 없으므로 쿼리 자체를 실제 DB 에서 확인한다 (UG-300 슬라이스 인프라).
 *
 * <p>{@code created_at} 은 {@code @CreatedDate} 가 채우고 {@code updatable = false} 라
 * 엔티티로는 과거 시각을 만들 수 없다. 그래서 네이티브 UPDATE 로 직접 밀어 넣는다 — 이
 * 테스트의 조건이 "시간" 이므로 그 값을 만들 수 없으면 아무것도 검증하지 못한다.
 */
@JpaSliceTest
@Import(HistoryPurgeRepository.class)
@DisplayName("UG-282: 이력 정리 대상 선별 쿼리")
class HistoryPurgeRepositorySliceTest {

    @Autowired private HistoryPurgeRepository repository;
    @Autowired private EntityManager em;

    private Project 프로젝트;

    @BeforeEach
    void setUp() {
        프로젝트 = Project.builder()
                .accountId(100L)
                .projectName("테스트")
                .branchName("branch-purge")
                .isDeleted(false)
                .status(ProjectStatus.ACTIVE)
                .build();
        em.persist(프로젝트);
    }

    private ai.univs.gate.modules.feature.domain.entity.BiometricFeature 특징점(
            Project project, String imagePath) {
        var f = ai.univs.gate.modules.feature.domain.entity.BiometricFeature.builder()
                .project(project)
                .type(FeatureType.FACE)
                .featureId("f-" + imagePath)
                .featureImagePath(imagePath)
                .isDeleted(false)
                .build();
        em.persist(f);
        return f;
    }

    private void 반영하고_비운다() {
        em.flush();
        em.clear();
    }

    private static LocalDateTime 며칠전(int days) {
        return LocalDateTime.now(ZoneOffset.UTC).minusDays(days);
    }

    /** 인증 이력 하나를 {@code days} 일 전에 생성된 것으로 만든다. */
    private Long 인증이력(int days, String 프로브경로, String 특징점경로) {
        return 인증이력(days, MatchType.IDENTIFY, 프로브경로, 특징점경로);
    }

    private Long 인증이력(int days, MatchType type, String 프로브경로, String 특징점경로) {
        MatchHistory h = MatchHistory.builder()
                .project(프로젝트)
                .featureType(FeatureType.FACE)
                .matchType(type)
                .matchTime(며칠전(days))
                .checkLiveness(false)
                .success(true)
                .matchedFeatureImagePath(프로브경로)
                .featureImagePath(특징점경로)
                .transactionUuid(UUID.randomUUID().toString())
                .build();
        em.persist(h);
        em.flush();
        나이를_준다("match_history", "match_history_id", h.getId(), days);
        return h.getId();
    }

    private Long 특징점이력(int days) {
        return 특징점이력(days, null);
    }

    private Long 특징점이력(int days, String 이미지경로) {
        FeatureHistory h = FeatureHistory.builder()
                .project(프로젝트)
                .featureType(FeatureType.FACE)
                .actionType(FeatureActionType.REGISTER)
                .featureImagePath(이미지경로)
                .success(true)
                .checkLiveness(false)
                .transactionUuid(UUID.randomUUID().toString())
                .build();
        em.persist(h);
        em.flush();
        나이를_준다("feature_history", "feature_history_id", h.getId(), days);
        return h.getId();
    }

    private void 나이를_준다(String table, String pk, Long id, int days) {
        em.createNativeQuery("UPDATE " + table + " SET created_at = :t WHERE " + pk + " = :id")
                .setParameter("t", 며칠전(days))
                .setParameter("id", id)
                .executeUpdate();
        em.clear();
    }

    @Nested
    @DisplayName("인증 이력")
    class 인증 {

        /**
         * 경계가 뒤집히면 <b>최근 것부터</b> 지운다. 이 한 줄이 그 방향을 고정한다.
         */
        @Test
        @DisplayName("기준보다 오래된 것만 고른다")
        void 기준보다_오래된_것만() {
            Long 오래된 = 인증이력(40, "probe/old.jpg", "feat/x.jpg");
            Long 최근 = 인증이력(10, "probe/new.jpg", "feat/x.jpg");

            var found = repository.findMatchHistoryToPurge(며칠전(30), 500);

            assertThat(found).extracting(MatchHistoryPurgeTarget::id)
                    .containsExactly(오래된).doesNotContain(최근);
        }

        /**
         * <b>가져오는 경로는 프로브 이미지여야 한다.</b>
         *
         * <p>서비스는 이 조회가 돌려준 두 번째 값을 그대로 지운다. 여기서
         * {@code feature_image_path} 가 나오면 <b>등록된 사용자의 사진</b>이 지워진다 — 그
         * 경로는 살아 있는 {@code biometric_feature} 와 같은 파일을 가리킨다.
         *
         * <p>그래서 두 컬럼에 <b>서로 다른 값</b>을 넣고 어느 쪽이 나오는지 본다. 같은 값을
         * 넣으면 이 테스트는 아무것도 증명하지 못한다.
         */
        /**
         * 조회가 <b>두 경로를 모두</b> 돌려줘야 한다.
         *
         * <p>어느 쪽을 지울지는 살아 있는 특징점이 가리키는지로 정해진다. 그 판단이 서게
         * 하려면 재료가 다 와야 한다 — 컬럼 하나가 빠지면 그 파일은 <b>영구 고아</b>가 된다
         * (행이 사라진 뒤 아무도 가리키지 않고, 다시 찾을 방법도 없다).
         *
         * <p>두 컬럼에 <b>서로 다른 값</b>을 넣는다. 같은 값이면 아무것도 증명하지 못한다.
         */
        @Test
        @DisplayName("두 이미지 경로를 모두 준다")
        void 두_경로를_모두_준다() {
            인증이력(40, MatchType.VERIFY_IMAGE, "probe/probe.jpg", "doc/id-card.jpg");

            var found = repository.findMatchHistoryToPurge(며칠전(30), 500);

            assertThat(found).hasSize(1);
            assertThat(found.get(0).candidateImagePaths())
                    .containsExactly("probe/probe.jpg", "doc/id-card.jpg");
        }

        /**
         * <b>종류를 가리지 않고 집는다.</b>
         *
         * <p>초판은 {@code REGISTER} 잔존 행을 쿼리에서 뺐는데, 그러면 그 행이 영구 면제된다 —
         * 개인정보를 파기하는 기능이 특정 행을 무기한 보유하는 셈이다. 파일을 지킬 책임은
         * 참조 검사로 옮겼으므로, 행은 종류와 무관하게 보존 기간으로만 판단한다.
         */
        @Test
        @DisplayName("REGISTER 잔존 행도 보존 기간이 지나면 대상이다")
        void 종류를_가리지_않는다() {
            Long 등록 = 인증이력(40, MatchType.REGISTER, "feat/registered.jpg", null);
            Long 인증 = 인증이력(40, MatchType.IDENTIFY, "probe/p.jpg", "feat/registered.jpg");

            var found = repository.findMatchHistoryToPurge(며칠전(30), 500);

            assertThat(found).extracting(MatchHistoryPurgeTarget::id)
                    .containsExactlyInAnyOrder(등록, 인증);
        }

        /**
         * 참조 검사 — 삭제 금지 목록을 고르는 쿼리.
         *
         * <p>이 쿼리가 빈 집합을 돌려주면 <b>등록된 사용자의 사진이 전부 지워진다.</b>
         * 되돌릴 수 없으므로 실제 DB 에서 확인한다.
         */
        @Test
        @DisplayName("살아 있는 특징점이 가리키는 경로만 돌려준다")
        void 참조_검사() {
            특징점(프로젝트, "feat/registered.jpg");
            반영하고_비운다();

            var 남길것 = repository.findPathsStillReferencedByFeatures(
                    List.of("feat/registered.jpg", "probe/p.jpg", "doc/id-card.jpg"));

            assertThat(남길것).containsExactly("feat/registered.jpg");
        }

        @Test
        @DisplayName("소프트 삭제된 특징점이 가리키는 경로도 남긴다")
        void 소프트_삭제된_특징점도_참조로_센다() {
            특징점(프로젝트, "feat/soft-deleted.jpg").delete();
            반영하고_비운다();

            assertThat(repository.findPathsStillReferencedByFeatures(
                    List.of("feat/soft-deleted.jpg")))
                    .as("행과 파일이 아직 남아 있다 — 지우는 것은 UG-303 의 일이다")
                    .containsExactly("feat/soft-deleted.jpg");
        }

        /**
         * 상한 자체를 고정한다.
         *
         * <p>H2 에는 {@code IN} 한도가 없어, 이 상수를 1,000,000 으로 바꿔도 다른 테스트는
         * 전부 초록이다 — 4차 반박 리뷰가 변이로 확인했다. 즉 <b>오라클 납품을 지키는 것이
         * 이 상수 하나뿐인데 아무도 방어하지 않는</b> 상태였다.
         *
         * <p>Oracle 19c 는 {@code IN} 리스트 1000 초과를 {@code ORA-01795} 로 거절한다.
         */
        @Test
        @DisplayName("IN 절 상한이 오라클 한도 안에 있다")
        void 상한이_오라클_한도_안이다() {
            assertThat(HistoryPurgeRepository.IN_절_상한)
                    .as("Oracle 19c 의 IN 리스트 한도는 1000 이다 (ORA-01795)")
                    .isLessThanOrEqualTo(1000)
                    .isPositive();
        }

        @Test
        @DisplayName("후보가 비면 쿼리를 날리지 않는다")
        void 참조_검사_빈_목록() {
            assertThat(repository.findPathsStillReferencedByFeatures(List.of())).isEmpty();
        }

        /**
         * {@code IN} 상한을 넘는 후보도 정확히 처리한다.
         *
         * <p>Oracle 19c 는 {@code IN} 리스트 1000 초과를 {@code ORA-01795} 로 거절하고, 배치
         * 500행 × 경로 2개 = 정확히 1000 이라 여유가 0 이었다. 리포지토리가 쪼개는데, 쪼개다
         * 마지막 조각을 빠뜨리면 <b>그 경로들이 검사 없이 지워진다</b> — 등록 사진이 사라진다.
         *
         * <p>상한(500)보다 큰 후보 목록을 주고, 마지막 조각에 있는 경로가 결과에 들어오는지를
         * 본다. H2 에는 IN 한도가 없으므로 이 테스트가 잡는 것은 한도 자체가 아니라
         * <b>쪼개기의 정확성</b>이다.
         */
        @Test
        @DisplayName("IN 상한을 넘는 후보도 빠짐없이 검사한다")
        void 상한을_넘는_후보를_쪼개서_검사한다() {
            특징점(프로젝트, "feat/first.jpg");
            특징점(프로젝트, "feat/last.jpg");
            반영하고_비운다();

            List<String> 후보 = new java.util.ArrayList<>();
            후보.add("feat/first.jpg");
            for (int i = 0; i < 700; i++) {
                후보.add("probe/filler-" + i + ".jpg");
            }
            후보.add("feat/last.jpg");   // 두 번째 조각에 들어간다

            assertThat(repository.findPathsStillReferencedByFeatures(후보))
                    .containsExactlyInAnyOrder("feat/first.jpg", "feat/last.jpg");
        }

        @Test
        @DisplayName("오래된 것부터 준다")
        void 오래된_것부터() {
            Long 가장오래 = 인증이력(50, "p/1", "f/1");
            Long 중간 = 인증이력(45, "p/2", "f/2");

            var found = repository.findMatchHistoryToPurge(며칠전(30), 500);

            assertThat(found).extracting(MatchHistoryPurgeTarget::id)
                    .containsExactly(가장오래, 중간);
        }

        @Test
        @DisplayName("상한을 넘겨 주지 않는다")
        void 상한을_지킨다() {
            인증이력(50, "p/1", "f/1");
            인증이력(45, "p/2", "f/2");
            인증이력(40, "p/3", "f/3");

            assertThat(repository.findMatchHistoryToPurge(며칠전(30), 2)).hasSize(2);
        }

        @Test
        @DisplayName("고른 것만 지운다")
        void 고른_것만_지운다() {
            Long 오래된 = 인증이력(40, "p/old", "f/x");
            Long 최근 = 인증이력(10, "p/new", "f/x");

            assertThat(repository.deleteMatchHistory(List.of(오래된))).isEqualTo(1);

            assertThat(em.find(MatchHistory.class, 오래된)).isNull();
            assertThat(em.find(MatchHistory.class, 최근)).isNotNull();
        }

        /** 빈 목록으로 {@code IN ()} 를 만들면 DB 에 따라 문법 오류다. */
        @Test
        @DisplayName("빈 목록이면 쿼리를 날리지 않는다")
        void 빈_목록() {
            assertThat(repository.deleteMatchHistory(List.of())).isZero();
        }
    }

    @Nested
    @DisplayName("특징점 이력")
    class 특징점 {

        @Test
        @DisplayName("기준보다 오래된 것만 고른다")
        void 기준보다_오래된_것만() {
            Long 오래된 = 특징점이력(40);
            Long 최근 = 특징점이력(10);

            assertThat(repository.findFeatureHistoryToPurge(며칠전(30), 500))
                    .extracting(MatchHistoryPurgeTarget::id)
                    .containsExactly(오래된).doesNotContain(최근);
        }

        @Test
        @DisplayName("고른 것만 지운다")
        void 고른_것만_지운다() {
            Long 오래된 = 특징점이력(40);
            Long 최근 = 특징점이력(10);

            assertThat(repository.deleteFeatureHistory(List.of(오래된))).isEqualTo(1);

            assertThat(em.find(FeatureHistory.class, 오래된)).isNull();
            assertThat(em.find(FeatureHistory.class, 최근)).isNotNull();
        }

        /**
         * <b>이쪽도 이미지 경로를 가져와야 한다.</b>
         *
         * <p>지금 {@code feature_history} 의 경로는 전부 등록된 특징점에서 복사된 값이라,
         * 가져오든 말든 참조 검사가 남기므로 결과가 같다. 그래서 이 단언이 없으면 변이로도
         * 잡히지 않는다 — 실제로 변이 실험에서 이 한 건만 초록이었다.
         *
         * <p>그런데 그 "결과가 같다" 는 <b>지금 데이터에 대해서만</b> 참이다. 자기 이미지를
         * 올리는 사건 종류가 추가되는 순간 여기만 조용히 영구 고아를 만든다. 이 티켓이 같은
         * 실패를 두 번 겪은 자리가 정확히 그것이다(VERIFY_IMAGE, 레거시 VERIFY).
         */
        @Test
        @DisplayName("이미지 경로를 후보로 올린다")
        void 이미지_경로를_가져온다() {
            특징점이력(40, "feat/registered.jpg");

            var found = repository.findFeatureHistoryToPurge(며칠전(30), 500);

            assertThat(found).hasSize(1);
            assertThat(found.get(0).candidateImagePaths())
                    .as("가져오지 않으면 참조 검사에 올라가지 못하고, 그 파일은 영구 고아가 된다")
                    .containsExactly("feat/registered.jpg");
        }

        @Test
        @DisplayName("빈 목록이면 쿼리를 날리지 않는다")
        void 빈_목록() {
            assertThat(repository.deleteFeatureHistory(List.of())).isZero();
        }
    }
}

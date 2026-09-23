package ai.univs.gate.support.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.support.jpa.JpaSliceTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 정리 대상을 고르는 쿼리 (UG-303).
 *
 * <p>이 쿼리들은 <b>제품 경로와 정반대 조건</b>을 본다. 다른 모든 조회가
 * {@code is_deleted = false} 를 거는데 여기는 {@code true} 인 것만 찾고, 찾은 것을 물리
 * 삭제한다. 조건 하나가 어긋나면 <b>살아 있는 프로젝트의 생체 데이터를 지운다.</b> 되돌릴 수
 * 없으므로 쿼리 자체를 실제 DB 에서 확인한다 (UG-300 슬라이스 인프라).
 */
@JpaSliceTest
@Import(ProjectPurgeRepository.class)
@DisplayName("UG-303: 정리 대상 선별 쿼리")
class ProjectPurgeRepositorySliceTest {

    @Autowired private ProjectPurgeRepository repository;
    @Autowired private EntityManager em;

    private Project 프로젝트(String branch, boolean deleted, LocalDateTime deletedAt) {
        Project p = Project.builder()
                .accountId(100L)
                .projectName("테스트")
                .branchName(branch)
                .isDeleted(deleted)
                .status(deleted ? ProjectStatus.DELETED : ProjectStatus.ACTIVE)
                .deletedAt(deletedAt)
                .build();
        em.persist(p);
        return p;
    }

    private BiometricFeature 특징점(Project project, boolean deleted) {
        BiometricFeature f = BiometricFeature.builder()
                .project(project)
                .type(FeatureType.FACE)
                .featureId("f-" + project.getBranchName() + (deleted ? "-d" : ""))
                .featureImagePath("img/x")
                .isDeleted(deleted)
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

    @Nested
    @DisplayName("대상 선별")
    class 대상_선별 {

        @Test
        @DisplayName("유예가 지난 삭제 프로젝트만 고른다")
        void 유예가_지난_것만() {
            Project 오래됨 = 프로젝트("old", true, 며칠전(40));
            Project 최근 = 프로젝트("recent", true, 며칠전(5));
            특징점(오래됨, false);
            특징점(최근, false);
            반영하고_비운다();

            List<Long> targets = repository.findPurgeTargetIds(며칠전(30), 10);

            assertThat(targets)
                    .as("유예 안에 있는 프로젝트를 지우면 복구 창이 사라진다")
                    .containsExactly(오래됨.getId());
        }

        /**
         * 가장 중요한 단언. 이 조건이 빠지면 살아 있는 프로젝트의 생체 데이터를 지운다.
         */
        @Test
        @DisplayName("살아 있는 프로젝트는 아무리 오래돼도 고르지 않는다")
        void 살아있는_것은_절대_아니다() {
            Project 살아있음 = 프로젝트("alive", false, null);
            // 삭제되지 않았는데 deleted_at 이 남아 있는 비정상 행까지 방어한다.
            Project 이상한행 = 프로젝트("weird", false, 며칠전(100));
            특징점(살아있음, false);
            특징점(이상한행, false);
            반영하고_비운다();

            List<Long> targets = repository.findPurgeTargetIds(며칠전(30), 10);

            assertThat(targets)
                    .as("is_deleted 조건이 사라지면 운영 데이터가 지워진다")
                    .doesNotContain(살아있음.getId(), 이상한행.getId())
                    .isEmpty();
        }

        @Test
        @DisplayName("삭제 시각을 모르는 행은 고르지 않는다 — V32 이전 데이터")
        void 삭제시각이_없으면_제외() {
            특징점(프로젝트("legacy", true, null), false);
            반영하고_비운다();

            assertThat(repository.findPurgeTargetIds(며칠전(30), 10))
                    .as("시각을 모르면 유예를 잴 수 없다. 임의로 지우는 것보다 남기는 쪽이 안전하다")
                    .isEmpty();
        }

        /**
         * 정리가 끝난 프로젝트는 목록에서 빠진다 — 이 잡의 종료 조건 (반박 리뷰 지적).
         *
         * <p>이 조건이 없으면 정리된 프로젝트가 계속 대상으로 남고, <b>가장 오래된 것부터</b>
         * 고르므로 실행당 상한을 영구히 차지한다. 그 뒤의 프로젝트는 영원히 정리되지 않는다.
         */
        @Test
        @DisplayName("지울 특징점이 없는 프로젝트는 고르지 않는다 — 종료 조건")
        void 정리가_끝나면_목록에서_빠진다() {
            Project 남은게있음 = 프로젝트("has-features", true, 며칠전(40));
            Project 정리끝 = 프로젝트("already-purged", true, 며칠전(400));
            특징점(남은게있음, false);
            반영하고_비운다();

            assertThat(repository.findPurgeTargetIds(며칠전(30), 10))
                    .as("정리된 프로젝트가 남으면 가장 오래된 것부터 상한을 채워 뒤가 막힌다")
                    .containsExactly(남은게있음.getId())
                    .doesNotContain(정리끝.getId());
        }

        @Test
        @DisplayName("상한을 넘겨 돌려주지 않는다")
        void 상한을_지킨다() {
            for (int i = 0; i < 5; i++) {
                특징점(프로젝트("p" + i, true, 며칠전(40 + i)), false);
            }
            반영하고_비운다();

            assertThat(repository.findPurgeTargetIds(며칠전(30), 3)).hasSize(3);
        }

        @Test
        @DisplayName("오래 방치된 것부터 처리한다")
        void 오래된_것부터() {
            Project 가장오래됨 = 프로젝트("a", true, 며칠전(100));
            Project 중간 = 프로젝트("b", true, 며칠전(60));
            특징점(가장오래됨, false);
            특징점(중간, false);
            반영하고_비운다();

            assertThat(repository.findPurgeTargetIds(며칠전(30), 10))
                    .containsExactly(가장오래됨.getId(), 중간.getId());
        }
    }

    /**
     * 삭제 시각이 실제로 찍히는가 (반박 리뷰 지적).
     *
     * <p>리뷰가 {@code Project.delete()} 의 {@code deletedAt} 대입을 통째로 지우는 변이를
     * 심었는데 전체 빌드가 통과했다. 그 한 줄이 이 기능 전체를 무장시키는 줄이다 — 없으면
     * 모든 행이 {@code deleted_at = NULL} 이 되고 잡은 아무것도 지우지 않는다. 조용히.
     */
    @Nested
    @DisplayName("삭제 시각 기록")
    class 삭제시각 {

        @Test
        @DisplayName("삭제하면 시각이 찍힌다 — 이 줄이 없으면 잡이 영원히 아무것도 안 한다")
        void 삭제하면_시각이_찍힌다() {
            Project p = 프로젝트("to-delete", false, null);
            반영하고_비운다();

            Project loaded = em.find(Project.class, p.getId());
            loaded.delete();
            반영하고_비운다();

            Project after = em.find(Project.class, p.getId());
            assertThat(after.isDeleted()).isTrue();
            assertThat(after.getDeletedAt())
                    .as("null 이면 findPurgeTargetIds 가 영원히 이 프로젝트를 고르지 않는다")
                    .isNotNull()
                    .isAfter(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        }

        /**
         * 이미 삭제된 프로젝트를 다시 삭제해도 시각을 덮어쓰지 않는다.
         *
         * <p>덮어쓰면 유예가 계속 뒤로 밀려 영원히 지워지지 않는다. 지금은 조회 경로가
         * {@code IsDeletedFalse} 로 막아 도달하지 않지만, 그 한 겹에 기대지 않는다.
         */
        @Test
        @DisplayName("다시 삭제해도 시각을 덮어쓰지 않는다 — 유예가 밀리지 않게")
        void 재삭제는_시각을_덮어쓰지_않는다() {
            LocalDateTime 처음 = 며칠전(100);
            Project p = 프로젝트("already", true, 처음);
            반영하고_비운다();

            Project loaded = em.find(Project.class, p.getId());
            loaded.delete();
            반영하고_비운다();

            assertThat(em.find(Project.class, p.getId()).getDeletedAt())
                    .as("덮어쓰면 유예가 매번 초기화되어 영원히 정리되지 않는다")
                    .isCloseTo(처음, org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.SECONDS));
        }
    }

    @Nested
    @DisplayName("프로젝트 재확인")
    class 재확인 {

        @Test
        @DisplayName("삭제된 프로젝트만 찾는다")
        void 삭제된_것만() {
            Project 삭제됨 = 프로젝트("gone", true, 며칠전(40));
            Project 살아있음 = 프로젝트("alive", false, null);
            반영하고_비운다();

            assertThat(repository.findDeletedProject(삭제됨.getId())).isPresent();
            assertThat(repository.findDeletedProject(살아있음.getId()))
                    .as("대상을 뽑은 뒤 복구된 프로젝트를 지우면 안 된다")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("특징점 조회·삭제")
    class 특징점_조회 {

        @Test
        @DisplayName("소프트 삭제된 특징점까지 포함한다")
        void 삭제된_것도_대상이다() {
            Project project = 프로젝트("p", true, 며칠전(40));
            특징점(project, false);
            특징점(project, true);
            반영하고_비운다();

            assertThat(repository.findFeaturesOf(project.getId()))
                    .as("소프트 삭제된 특징점도 행과 이미지가 남아 있다 — 오히려 더 오래 방치된 데이터다")
                    .hasSize(2);
        }

        @Test
        @DisplayName("다른 프로젝트의 특징점은 건드리지 않는다")
        void 프로젝트_경계를_지킨다() {
            Project 대상 = 프로젝트("target", true, 며칠전(40));
            Project 남의것 = 프로젝트("other", false, null);
            특징점(대상, false);
            특징점(남의것, false);
            반영하고_비운다();

            assertThat(repository.findFeaturesOf(대상.getId())).hasSize(1);
            assertThat(repository.findFeaturesOf(남의것.getId())).hasSize(1);
        }

        @Test
        @DisplayName("물리 삭제한다 — 행이 실제로 사라진다")
        void 물리_삭제다() {
            Project project = 프로젝트("p", true, 며칠전(40));
            특징점(project, false);
            반영하고_비운다();

            BiometricFeature loaded = repository.findFeaturesOf(project.getId()).get(0);
            repository.deleteFeature(loaded);
            반영하고_비운다();

            assertThat(repository.findFeaturesOf(project.getId()))
                    .as("소프트 삭제로 바뀌면 이 기능의 목적(실제 삭제)이 사라진다")
                    .isEmpty();
        }
    }
}

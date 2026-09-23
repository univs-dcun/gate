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
        FeatureHistory h = FeatureHistory.builder()
                .project(프로젝트)
                .featureType(FeatureType.FACE)
                .actionType(FeatureActionType.REGISTER)
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
         * 조회가 두 경로와 {@code match_type} 을 모두 돌려줘야 한다.
         *
         * <p>어느 쪽을 지울지는 {@link MatchHistoryPurgeTarget#ownedImagePaths()} 가 고른다.
         * 그 판단이 서게 하려면 재료가 다 와야 한다 — 컬럼 하나가 빠지면 서비스는 지워야 할
         * 파일을 지우지 못하거나(영구 고아) 지우면 안 될 파일을 지운다.
         *
         * <p>두 컬럼에 <b>서로 다른 값</b>을 넣는다. 같은 값이면 이 테스트는 아무것도
         * 증명하지 못한다.
         */
        @Test
        @DisplayName("두 이미지 경로와 match_type 을 모두 준다")
        void 두_경로를_모두_준다() {
            인증이력(40, MatchType.VERIFY_IMAGE, "probe/probe.jpg", "doc/id-card.jpg");

            var found = repository.findMatchHistoryToPurge(며칠전(30), 500);

            assertThat(found).hasSize(1);
            assertThat(found.get(0).matchedFeatureImagePath()).isEqualTo("probe/probe.jpg");
            assertThat(found.get(0).featureImagePath()).isEqualTo("doc/id-card.jpg");
            assertThat(found.get(0).matchType()).isEqualTo(MatchType.VERIFY_IMAGE);
            assertThat(found.get(0).ownedImagePaths())
                    .as("VERIFY_IMAGE 의 두 경로는 모두 이 행만의 것이다")
                    .containsExactly("probe/probe.jpg", "doc/id-card.jpg");
        }

        /**
         * <b>REGISTER 잔존 행은 아예 집지 않는다</b> (반박 리뷰 지적).
         *
         * <p>등록은 이제 {@code feature_history} 의 사건이고 V27 이 {@code match_history} 의
         * REGISTER 행을 지웠다 — 다만 짝이 없는 행은 <b>일부러 남겼다.</b> 그 잔존 행은
         * {@code matched_feature_image_path} 에 <b>등록 이미지</b>를 담고 있어서, 집는 순간
         * 살아 있는 특징점의 사진을 지운다. 되돌릴 수 없다.
         */
        @Test
        @DisplayName("REGISTER 잔존 행은 대상에서 빠진다")
        void 등록_잔존행은_건드리지_않는다() {
            Long 등록 = 인증이력(40, MatchType.REGISTER, "feat/registered.jpg", "feat/registered.jpg");
            Long 인증 = 인증이력(40, MatchType.IDENTIFY, "probe/p.jpg", "feat/registered.jpg");

            var found = repository.findMatchHistoryToPurge(며칠전(30), 500);

            assertThat(found).extracting(MatchHistoryPurgeTarget::id)
                    .containsExactly(인증).doesNotContain(등록);
        }

        /** 1:N·1:1(id) 계열의 {@code feature_image_path} 는 공유 경로라 지울 목록에 없다. */
        @Test
        @DisplayName("IDENTIFY 행이 지울 파일은 프로브 이미지뿐이다")
        void 인증행은_프로브만_지운다() {
            인증이력(40, MatchType.IDENTIFY, "probe/p.jpg", "feat/registered.jpg");

            var found = repository.findMatchHistoryToPurge(며칠전(30), 500);

            assertThat(found.get(0).ownedImagePaths()).containsExactly("probe/p.jpg");
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

        @Test
        @DisplayName("빈 목록이면 쿼리를 날리지 않는다")
        void 빈_목록() {
            assertThat(repository.deleteFeatureHistory(List.of())).isZero();
        }
    }
}

package ai.univs.gate.support.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.jpa.JpaSliceTest;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 삭제한 특징점의 이미지 파기 (UG-347) — 실제 쿼리와 {@code REQUIRES_NEW} 커밋으로 본다.
 *
 * <p>파기는 자기 트랜잭션에서 커밋하므로 테스트 트랜잭션 안에서는 보이지 않는다. 그래서 이 클래스는 트랜잭션 없이 돌고,
 * 만든 행은 끝날 때 지운다. 같은 슬라이스 DB 를 다른 클래스도 쓰므로 대상 목록은 「포함·미포함」으로만 본다.
 */
@JpaSliceTest
@Import({DeletedFeatureImagePurgeService.class, DeletedFeatureImagePurgeRepository.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("UG-347: 삭제한 특징점의 이미지 파기")
class DeletedFeatureImagePurgeSliceTest {

    @MockitoBean private FileService fileService;

    @Autowired private DeletedFeatureImagePurgeService service;
    @Autowired private DeletedFeatureImagePurgeRepository repository;
    @Autowired private EntityManager em;
    @Autowired private TransactionTemplate tx;

    private Project project;
    private final List<Long> created = new ArrayList<>();

    @BeforeEach
    void setUp() {
        project = tx.execute(status -> {
            Project p = Project.builder().accountId(347L).projectName("ug347").branchName("branch-ug347")
                    .isDeleted(false).status(ProjectStatus.ACTIVE).build();
            em.persist(p);
            return p;
        });
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> {
            em.createQuery("DELETE FROM BiometricFeature f WHERE f.id IN :ids").setParameter("ids", created.isEmpty() ? List.of(-1L) : created).executeUpdate();
            em.createQuery("DELETE FROM Project p WHERE p.id = :id").setParameter("id", project.getId()).executeUpdate();
        });
    }

    private Long 특징점(String path, boolean deleted) {
        Long id = tx.execute(status -> {
            BiometricFeature f = BiometricFeature.builder().project(em.find(Project.class, project.getId()))
                    .type(FeatureType.FACE).featureId("f-" + System.nanoTime()).featureImagePath(path)
                    .isDeleted(deleted).build();
            em.persist(f);
            return f.getId();
        });
        created.add(id);
        return id;
    }

    private String 경로(Long id) {
        return tx.execute(status -> em.find(BiometricFeature.class, id).getFeatureImagePath());
    }

    @Test
    @DisplayName("대상은 삭제됐고 경로가 남은 것뿐이다 — 살아 있는 것·이미 비운 것은 빠진다, 커서 다음부터")
    void 대상() {
        Long 삭제됨 = 특징점("/face/a.jpg", true);
        Long 살아있음 = 특징점("/face/b.jpg", false);
        Long 비움 = 특징점(null, true);
        Long 삭제됨2 = 특징점("/face/c.jpg", true);

        List<Long> all = service.findTargets(0L, 10_000);
        assertThat(all).contains(삭제됨, 삭제됨2).doesNotContain(살아있음, 비움);
        assertThat(all).isSorted();
        assertThat(service.findTargets(삭제됨, 10_000)).contains(삭제됨2).doesNotContain(삭제됨);
        assertThat(service.findTargets(0L, 1)).hasSize(1);
    }

    @Test
    @DisplayName("파일을 지우고 경로를 비운다 — 다음 조회에서 빠진다")
    void 파기() {
        Long id = 특징점("/face/d.jpg", true);

        assertThat(service.purge(id)).isTrue();

        verify(fileService).delete("/face/d.jpg");
        assertThat(경로(id)).isNull();
        assertThat(service.findTargets(0L, 10_000)).doesNotContain(id);
        assertThat(service.purge(id)).as("두 번째는 대상이 아니다").isFalse();
    }

    @Test
    @DisplayName("파일 삭제가 실패하면 경로를 남긴다 — 다음 실행이 다시 집는다")
    void 파일_삭제_실패() {
        Long id = 특징점("/face/e.jpg", true);
        willThrow(new CustomGateException(ErrorType.INVALID_FILE_PATH)).given(fileService).delete("/face/e.jpg");

        assertThatThrownBy(() -> service.purge(id)).isInstanceOf(CustomGateException.class);

        assertThat(경로(id)).isEqualTo("/face/e.jpg");
        assertThat(service.findTargets(0L, 10_000)).contains(id);
    }

    @Test
    @DisplayName("purgeQuietly 는 실패를 삼킨다 — 삭제 API 응답을 바꾸지 않는다")
    void 조용히() {
        Long id = 특징점("/face/f.jpg", true);
        willThrow(new CustomGateException(ErrorType.INVALID_FILE_PATH)).given(fileService).delete(anyString());

        service.purgeQuietly(id);

        assertThat(경로(id)).isEqualTo("/face/f.jpg");
    }

    @Test
    @DisplayName("살아 있는 특징점이 같은 파일을 쓰면 파일은 남기고 이 행의 경로만 비운다")
    void 공유_파일() {
        Long 삭제됨 = 특징점("/face/shared.jpg", true);
        Long 살아있음 = 특징점("/face/shared.jpg", false);

        assertThat(service.purge(삭제됨)).isTrue();

        verify(fileService, never()).delete(anyString());
        assertThat(경로(삭제됨)).isNull();
        assertThat(경로(살아있음)).isEqualTo("/face/shared.jpg");
    }

    @Test
    @DisplayName("살아 있는 특징점은 건드리지 않는다")
    void 살아있음() {
        Long id = 특징점("/face/g.jpg", false);

        assertThat(service.purge(id)).isFalse();

        verify(fileService, never()).delete(anyString());
        assertThat(경로(id)).isEqualTo("/face/g.jpg");
    }

    @Test
    @DisplayName("빈 경로는 파일 없이 비운다 — FileService 가 빈 경로를 거절해 매번 실패하며 남지 않게")
    void 빈_경로() {
        Long id = 특징점("", true);

        assertThat(service.purge(id)).isTrue();

        verify(fileService, never()).delete(anyString());
        assertThat(경로(id)).isNull();
    }

    @Test
    @DisplayName("경로 비우기는 읽은 경로와 같을 때만 — 그사이 바뀌었으면 0행")
    void 조건부_비우기() {
        Long id = 특징점("/face/h.jpg", true);

        Integer 다른_경로 = tx.execute(status -> repository.clearImagePath(id, "/face/other.jpg"));
        Integer 같은_경로 = tx.execute(status -> repository.clearImagePath(id, "/face/h.jpg"));
        assertThat(다른_경로).isZero();
        assertThat(같은_경로).isEqualTo(1);
    }
}

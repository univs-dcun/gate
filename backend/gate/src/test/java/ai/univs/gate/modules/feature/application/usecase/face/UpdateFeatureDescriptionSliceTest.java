package ai.univs.gate.modules.feature.application.usecase.face;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.infrastructure.persistence.ApiKeyRepositoryImpl;
import ai.univs.gate.modules.feature.application.input.UpdateFeatureDescriptionInput;
import ai.univs.gate.modules.feature.application.usecase.palm.UpdatePalmFeatureDescriptionUseCase;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.feature.infrastructure.persistence.BiometricFeatureDSLRepository;
import ai.univs.gate.modules.feature.infrastructure.persistence.BiometricFeatureRepositoryImpl;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.modules.project.infrastructure.persistence.ProjectLivenessSettingRepositoryImpl;
import ai.univs.gate.modules.project.infrastructure.persistence.ProjectSettingsRepositoryImpl;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyLookupCache;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.jpa.JpaSliceTest;
import ai.univs.gate.support.project.ProjectSettingsService;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * UG-376: 메모 수정이 실제 DB 에 반영되는가, 그리고 동시 삭제를 되살리지 않는가 (반박 리뷰 H·M).
 *
 * <p>단위 테스트는 목이라 {@code @Transactional} 을 빼도(변경 감지가 flush 되지 않아 DB 에 안 남아도) 통과했다. 여기서는 실제
 * 트랜잭션·H2 로 본다. 경합 테스트는 삭제(UG-345 와 같은 잠금 → {@code delete()})가 행을 잠근 채 커밋하기 전에 메모 수정이 들어오게
 * 한다. 잠그지 않고 읽던 처음 구현은 여기서 삭제를 {@code is_deleted = false} 로 되돌렸다.
 *
 * <p>트랜잭션 없이 돈다({@code NOT_SUPPORTED}) — 테스트 트랜잭션이 커넥션과 잠금을 쥐면 경합을 만들 수 없다. 풀은 둘이다(삭제 쪽·수정 쪽).
 */
@JpaSliceTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gate-ug376;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.connection-timeout=2000"
})
@Import({UpdateFaceFeatureDescriptionUseCase.class, UpdatePalmFeatureDescriptionUseCase.class,
        ApiKeyService.class, ApiKeyLookupCache.class, ProjectSettingsService.class,
        ApiKeyRepositoryImpl.class, BiometricFeatureRepositoryImpl.class, BiometricFeatureDSLRepository.class,
        ProjectSettingsRepositoryImpl.class, ProjectLivenessSettingRepositoryImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("UG-376: 메모 수정 — 실제 트랜잭션")
class UpdateFeatureDescriptionSliceTest {

    private static final String KEY = "gate_ug376aaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final long OWNER = 376L;

    @MockitoBean private FileService fileService;

    @Autowired private UpdateFaceFeatureDescriptionUseCase updateFace;
    @Autowired private UpdatePalmFeatureDescriptionUseCase updatePalm;
    @Autowired private BiometricFeatureRepository biometricFeatureRepository;
    @Autowired private ApiKeyLookupCache apiKeyLookupCache;
    @Autowired private EntityManager em;
    @Autowired private TransactionTemplate tx;
    @Autowired private JdbcTemplate jdbc;

    private Project project;
    private Long faceId;
    private Long palmId;

    @BeforeEach
    void setUp() {
        apiKeyLookupCache.invalidateAll();
        // UG-386: 응답의 사진 URL 은 ImageUrls 가 만든다 — 이 테스트는 DB 반영만 보므로 서명 없는 가짜로 둔다
        org.mockito.BDDMockito.given(fileService.imageUrls(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyBoolean()))
                .willAnswer(ai.univs.gate.support.file.TestImageUrls.prefix("https://gate.test/api/v1/file?filePath="));
        tx.executeWithoutResult(status -> {
            project = Project.builder()
                    .accountId(OWNER).projectName("ug376").branchName("branch-ug376")
                    .isDeleted(false).status(ProjectStatus.ACTIVE).build();
            em.persist(project);
            em.persist(ApiKey.builder().project(project).apiKey(KEY).secretKey("secret-ug376")
                    .isActive(true).issuedAt(LocalDateTime.now(ZoneOffset.UTC)).build());
            em.persist(ProjectSettings.builder().project(project).consentEnabled(true).build());
            BiometricFeature face = BiometricFeature.builder().project(project).type(FeatureType.FACE)
                    .featureId("fid-376").featureImagePath("img/376.jpg").description("old").isDeleted(false).build();
            BiometricFeature palm = BiometricFeature.builder().project(project).type(FeatureType.PALM)
                    .featureId("pid-376").featureImagePath("img/376p.jpg").description("old palm").isDeleted(false).build();
            em.persist(face);
            em.persist(palm);
            em.flush();
            faceId = face.getId();
            palmId = palm.getId();
        });
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> {
            em.createQuery("DELETE FROM BiometricFeature f WHERE f.project.id = :p").setParameter("p", project.getId()).executeUpdate();
            em.createQuery("DELETE FROM ApiKey k WHERE k.project.id = :p").setParameter("p", project.getId()).executeUpdate();
            em.createQuery("DELETE FROM ProjectSettings s WHERE s.project.id = :p").setParameter("p", project.getId()).executeUpdate();
            em.createQuery("DELETE FROM Project p WHERE p.id = :p").setParameter("p", project.getId()).executeUpdate();
        });
    }

    private String 메모(Long id) {
        return jdbc.queryForObject("SELECT description FROM biometric_feature WHERE biometric_feature_id = ?", String.class, id);
    }

    private boolean 지워짐(Long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT is_deleted FROM biometric_feature WHERE biometric_feature_id = ?", Boolean.class, id));
    }

    private static UpdateFeatureDescriptionInput 입력(Long id, String description) {
        return new UpdateFeatureDescriptionInput(OWNER, KEY, id, description);
    }

    @Test
    @DisplayName("얼굴·팜: 커밋되어 DB 에 남고, updated_at 이 바뀐다 — 다른 컬럼은 그대로")
    void 반영된다() {
        LocalDateTime before = jdbc.queryForObject(
                "SELECT updated_at FROM biometric_feature WHERE biometric_feature_id = ?", LocalDateTime.class, faceId);

        updateFace.execute(입력(faceId, "new memo"));
        updatePalm.execute(입력(palmId, ""));

        assertThat(메모(faceId)).isEqualTo("new memo");
        assertThat(메모(palmId)).as("비우면 null").isNull();
        LocalDateTime after = jdbc.queryForObject(
                "SELECT updated_at FROM biometric_feature WHERE biometric_feature_id = ?", LocalDateTime.class, faceId);
        assertThat(after).isAfterOrEqualTo(before);
        assertThat(지워짐(faceId)).isFalse();
        assertThat(jdbc.queryForObject("SELECT feature_image_path FROM biometric_feature WHERE biometric_feature_id = ?",
                String.class, faceId)).isEqualTo("img/376.jpg");
    }

    @Test
    @DisplayName("이미 지워진 특징점이면 INVALID_USER, 아무것도 바뀌지 않는다")
    void 지워진_특징점() {
        tx.executeWithoutResult(s -> biometricFeatureRepository
                .findForUpdateByIdAndTypeAndIsDeletedFalse(faceId, FeatureType.FACE).orElseThrow().delete());

        assertThatThrownBy(() -> updateFace.execute(입력(faceId, "zombie")))
                .isInstanceOf(CustomGateException.class)
                .extracting(e -> ((CustomGateException) e).getErrorType())
                .isEqualTo(ErrorType.INVALID_USER);
        assertThat(지워짐(faceId)).isTrue();
        assertThat(메모(faceId)).isEqualTo("old");
    }

    /** 반박 리뷰 H — 삭제가 잠그고 커밋하기 전에 메모 수정이 오면, 수정은 기다렸다가 「없음」으로 끝나고 삭제는 남는다. */
    @Test
    @DisplayName("동시 삭제: 삭제가 행을 잠근 사이 들어온 메모 수정은 기다린 뒤 INVALID_USER — 지운 특징점을 되살리지 않는다")
    void 동시_삭제를_되살리지_않는다() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        ExecutorService deleter = Executors.newSingleThreadExecutor();
        try {
            Future<?> deleting = deleter.submit(() -> tx.executeWithoutResult(s -> {
                biometricFeatureRepository.findForUpdateByIdAndTypeAndIsDeletedFalse(faceId, FeatureType.FACE)
                        .orElseThrow().delete();
                em.flush();
                locked.countDown();
                try {
                    Thread.sleep(500);   // 수정 요청이 잠금 앞에 와서 기다리게
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> updateFace.execute(입력(faceId, "late memo")))
                    .isInstanceOf(CustomGateException.class)
                    .extracting(e -> ((CustomGateException) e).getErrorType())
                    .isEqualTo(ErrorType.INVALID_USER);
            deleting.get(5, TimeUnit.SECONDS);
        } finally {
            deleter.shutdownNow();
        }

        assertThat(지워짐(faceId)).as("삭제가 살아 있다").isTrue();
        assertThat(메모(faceId)).isEqualTo("old");
    }
}

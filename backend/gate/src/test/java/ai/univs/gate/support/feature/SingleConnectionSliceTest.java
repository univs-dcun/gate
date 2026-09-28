package ai.univs.gate.support.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.infrastructure.persistence.ApiKeyRepositoryImpl;
import ai.univs.gate.modules.feature.application.input.face.DeleteFaceFeatureInput;
import ai.univs.gate.modules.feature.application.usecase.face.DeleteFaceFeatureUseCase;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureActionType;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.CreateFaceFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.DeleteFaceFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.persistence.BiometricFeatureDSLRepository;
import ai.univs.gate.modules.feature.infrastructure.persistence.BiometricFeatureRepositoryImpl;
import ai.univs.gate.modules.feature.infrastructure.persistence.FeatureHistoryRepositoryImpl;
import ai.univs.gate.modules.feature.infrastructure.persistence.MatchHistoryRepositoryImpl;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.modules.project.infrastructure.persistence.ProjectLivenessSettingRepositoryImpl;
import ai.univs.gate.modules.project.infrastructure.persistence.ProjectSettingsRepositoryImpl;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.face.FaceFeatureService;
import ai.univs.gate.support.feature.face.FaceService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.history.HistoryRecorder;
import ai.univs.gate.support.jpa.JpaSliceTest;
import ai.univs.gate.support.project.ProjectSettingsService;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 커넥션 풀이 <b>하나뿐일 때</b> 등록·삭제가 끝까지 도는가 (UG-336).
 *
 * <p>나머지 테스트는 "선언이 없다"(선언 가드)와 "호출 순서가 맞다"(단위 테스트)만 본다. 이
 * 클래스는 티켓의 주장 자체 — <b>요청당 커넥션 하나</b> — 를 실제 풀로 잰다. UG-336 이 처음
 * 교착을 재현한 방식(풀 크기 1)과 같다.
 *
 * <p>풀이 하나면 요청이 커넥션을 둘 요구하는 순간 두 번째가 오지 않아 {@code connectionTimeout}
 * 뒤에 실패한다. 즉 이 테스트가 통과한다는 것은 <b>어느 시점에도 둘을 쥐지 않았다</b>는 뜻이다.
 * 운영에서는 같은 조건이 "풀 크기만큼의 동시 요청" 으로 만들어진다.
 *
 * <p>원격 호출 목(mock)은 그 안에서 <b>커넥션을 하나 빌려 본다.</b> 호출자가 원격 호출 동안
 * 커넥션을 쥐고 있으면 빌리지 못한다. 옛 구조에서 가장 길었던 창(원격 호출 내내 커넥션 하나)을
 * 직접 확인하는 장치다.
 *
 * <p>트랜잭션 없이 돈다({@code NOT_SUPPORTED}). {@code @DataJpaTest} 가 기본으로 여는 테스트
 * 트랜잭션은 그 자체로 커넥션을 하나 쥐어, 검증하려는 조건을 하네스가 망가뜨린다.
 *
 * <p>DB 는 따로 쓴다. 다른 슬라이스와 같은 인메모리 DB 를 공유하면 이 컨텍스트의
 * {@code create-drop} 이 그쪽 테이블을 지운다.
 */
@JpaSliceTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gate-ug336;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.minimum-idle=1",
        // Hikari 의 하한이 250ms 다. 짧을수록 실패가 빨리 드러나고, 1초면 느린 CI 에서도
        // 정상 경로가 오판되지 않는다 — 정상 경로는 기다릴 일이 아예 없다.
        "spring.datasource.hikari.connection-timeout=1000"
})
@Import({FaceFeatureService.class, DeleteFaceFeatureUseCase.class, HistoryRecorder.class,
        ApiKeyService.class, ProjectSettingsService.class,
        ApiKeyRepositoryImpl.class, BiometricFeatureRepositoryImpl.class,
        BiometricFeatureDSLRepository.class, MatchHistoryRepositoryImpl.class,
        FeatureHistoryRepositoryImpl.class, ProjectSettingsRepositoryImpl.class,
        ProjectLivenessSettingRepositoryImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("UG-336: 풀 크기 1 에서 등록·삭제")
class SingleConnectionSliceTest {

    private static final String KEY = "gate_ug336aaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String BRANCH = "branch-ug336";
    private static final long OWNER = 336L;

    @MockitoBean private FaceService faceService;
    @MockitoBean private FileService fileService;

    @Autowired private FaceFeatureService faceFeatureService;
    @Autowired private DeleteFaceFeatureUseCase deleteFaceFeatureUseCase;
    @Autowired private HistoryRecorder historyRecorder;
    @Autowired private DataSource dataSource;
    @Autowired private EntityManager em;
    @Autowired private TransactionTemplate tx;

    private Project project;

    /** 원격 호출 안에서 커넥션을 빌려 본 결과. 빌렸으면 null, 못 빌렸으면 그 예외. */
    private final AtomicReference<Throwable> 원격중_커넥션_실패 = new AtomicReference<>();
    private boolean 원격_호출됨;

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(status -> {
            project = Project.builder()
                    .accountId(OWNER).projectName("ug336").branchName(BRANCH)
                    .isDeleted(false).status(ProjectStatus.ACTIVE).build();
            em.persist(project);
            em.persist(ApiKey.builder()
                    .project(project).apiKey(KEY).secretKey("secret-ug336")
                    .isActive(true).issuedAt(LocalDateTime.now(ZoneOffset.UTC)).build());
            em.persist(ProjectSettings.builder().project(project).consentEnabled(true).build());
        });
        given(fileService.uploadIfConsent(any(), anyBoolean())).willReturn("img/ug336.jpg");
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> {
            em.createQuery("DELETE FROM FeatureHistory h WHERE h.project.id = :p")
                    .setParameter("p", project.getId()).executeUpdate();
            em.createQuery("DELETE FROM BiometricFeature f WHERE f.project.id = :p")
                    .setParameter("p", project.getId()).executeUpdate();
            em.createQuery("DELETE FROM ApiKey k WHERE k.project.id = :p")
                    .setParameter("p", project.getId()).executeUpdate();
            em.createQuery("DELETE FROM ProjectSettings s WHERE s.project.id = :p")
                    .setParameter("p", project.getId()).executeUpdate();
            em.createQuery("DELETE FROM Project p WHERE p.id = :p")
                    .setParameter("p", project.getId()).executeUpdate();
        });
    }

    /** 원격 호출 목의 본체 — 그 순간 풀에 남는 커넥션이 있는지 본다. */
    private void 원격_호출_중_커넥션을_빌려_본다() {
        원격_호출됨 = true;
        try (Connection ignored = dataSource.getConnection()) {
            // 빌렸다 = 호출자가 원격 호출 동안 커넥션을 쥐고 있지 않다.
        } catch (Exception e) {
            원격중_커넥션_실패.set(e);
        }
    }

    /**
     * 전제 — 정말 풀이 하나인가.
     *
     * <p>{@code @DataJpaTest} 가 데이터소스를 다른 것으로 바꿔 끼우면(기본 동작) 위 설정이
     * 무시되고, 이 클래스의 나머지 테스트는 아무것도 증명하지 못한 채 통과한다.
     */
    @Test
    @DisplayName("전제: 데이터소스는 크기 1 인 Hikari 풀이다")
    void 전제_풀_크기_1() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) dataSource).getMaximumPoolSize()).isEqualTo(1);
    }

    /**
     * <b>대조군</b> — 옛 구조는 여기서 막힌다.
     *
     * <p>바깥 트랜잭션이 커넥션을 쥔 채 {@code HistoryRecorder.start}({@code REQUIRES_NEW})가
     * 두 번째를 요구하는 것이 UG-336 이전 등록·삭제의 모양이었다. 풀이 하나면 그 두 번째가 오지
     * 않는다. 이것이 실패하지 않으면 아래 테스트의 통과는 의미가 없다.
     */
    @Test
    @DisplayName("대조군: 바깥 트랜잭션 안에서 start 를 부르면 두 번째 커넥션을 얻지 못한다")
    void 대조군_옛_구조는_막힌다() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            em.createQuery("SELECT COUNT(p) FROM Project p").getSingleResult();   // 첫 번째 확보
            historyRecorder.start(FeatureHistory.register(
                    project, FeatureType.FACE, false, null, UUID.randomUUID().toString(), true));
        })).isInstanceOf(CannotCreateTransactionException.class);
    }

    @Test
    @DisplayName("등록: 커넥션 하나로 끝나고, 원격 호출 동안에는 커넥션을 쥐지 않는다")
    void 등록() {
        given(faceService.createFace(any(CreateFaceFeignRequestDTO.class))).willAnswer(inv -> {
            원격_호출_중_커넥션을_빌려_본다();
            return "face-ug336";
        });

        var result = faceFeatureService.createFaceFeature(CallerType.API, OWNER, KEY,
                new MockMultipartFile("image", "a.jpg", "image/jpeg", new byte[]{1, 2, 3}),
                "d", UUID.randomUUID().toString(), null);

        assertThat(원격_호출됨).isTrue();
        assertThat(원격중_커넥션_실패.get())
                .as("원격 호출 동안 호출자가 커넥션을 쥐고 있다 — 메서드 전체가 트랜잭션으로 되돌아갔는가")
                .isNull();

        // 특징점과 성공 이력이 실제로 커밋됐는지 — 새 트랜잭션에서 다시 읽는다.
        tx.executeWithoutResult(status -> {
            BiometricFeature saved = em.find(BiometricFeature.class, result.biometricFeature().getId());
            assertThat(saved).isNotNull();
            assertThat(saved.getFeatureId()).isEqualTo("face-ug336");

            FeatureHistory history = em.createQuery(
                            "SELECT h FROM FeatureHistory h WHERE h.project.id = :p", FeatureHistory.class)
                    .setParameter("p", project.getId()).getSingleResult();
            assertThat(history.getActionType()).isEqualTo(FeatureActionType.REGISTER);
            assertThat(history.isSuccess()).isTrue();
            assertThat(history.getFeatureSeq()).isEqualTo(saved.getId());
        });
    }

    @Test
    @DisplayName("삭제: 커넥션 하나로 끝나고, 원격 호출 동안에는 커넥션을 쥐지 않는다")
    void 삭제() {
        Long featureSeq = tx.execute(status -> {
            BiometricFeature f = BiometricFeature.builder()
                    .project(em.find(Project.class, project.getId())).type(FeatureType.FACE)
                    .featureId("face-to-delete").isDeleted(false).build();
            em.persist(f);
            return f.getId();
        });
        willAnswer(inv -> {
            원격_호출_중_커넥션을_빌려_본다();
            return null;
        }).given(faceService).deleteFace(any(DeleteFaceFeignRequestDTO.class));

        deleteFaceFeatureUseCase.execute(new DeleteFaceFeatureInput(OWNER, KEY, featureSeq));

        assertThat(원격_호출됨).isTrue();
        assertThat(원격중_커넥션_실패.get())
                .as("원격 호출 동안 호출자가 커넥션을 쥐고 있다 — 메서드 전체가 트랜잭션으로 되돌아갔는가")
                .isNull();

        tx.executeWithoutResult(status -> {
            assertThat(em.find(BiometricFeature.class, featureSeq).isDeleted())
                    .as("소프트 삭제가 성공 트랜잭션에서 커밋돼야 한다").isTrue();
            FeatureHistory history = em.createQuery(
                            "SELECT h FROM FeatureHistory h WHERE h.project.id = :p", FeatureHistory.class)
                    .setParameter("p", project.getId()).getSingleResult();
            assertThat(history.getActionType()).isEqualTo(FeatureActionType.DELETE);
            assertThat(history.isSuccess()).isTrue();
        });
    }
}

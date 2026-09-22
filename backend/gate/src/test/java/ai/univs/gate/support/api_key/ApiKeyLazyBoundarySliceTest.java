package ai.univs.gate.support.api_key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.infrastructure.persistence.ApiKeyRepositoryImpl;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.support.jpa.JpaSliceTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 트랜잭션 밖에서도 {@code getProject()} 를 읽을 수 있는가 (UG-335).
 *
 * <p><b>이 테스트가 없으면 UG-335 는 검증되지 않는다.</b> 나머지 가드는 전부 "선언이 남아
 * 있는가" 만 본다. {@code @Transactional(readOnly = true)} 가 {@code ApiKeyService} 에 붙어
 * 있다는 사실과, 그것이 실제로 프록시를 초기화해 준다는 사실은 다른 이야기다.
 *
 * <p>그래서 이 클래스는 <b>트랜잭션 없이</b> 돈다
 * ({@code @Transactional(propagation = NOT_SUPPORTED)}). {@code @DataJpaTest} 는 기본적으로
 * 테스트마다 트랜잭션을 열어 주는데, 그러면 영속성 컨텍스트가 계속 열려 있어 지연 로딩이
 * 무조건 성공한다 — 검증하려는 조건을 테스트 하네스가 지워 버린다. 운영에서 컨트롤러가
 * 유스케이스를 부르는 상황이 바로 "트랜잭션 없음" 이므로, 그 조건을 그대로 재현한다.
 *
 * <p><b>{@code open-in-view} 와의 관계.</b> 슬라이스에는 웹 계층이 없으므로 OSIV 필터 자체가
 * 없다. 즉 여기는 항상 "OSIV 꺼짐" 과 같은 조건이고, UG-335 이후의 배포 환경과 같다. 반대로
 * UG-335 이전의 배포 환경(OSIV 켜짐)은 여기서 재현되지 않는다 — 그쪽은 요청 스코프 컨텍스트가
 * 실패를 가려 주던 상태이고, 이 테스트가 확인하려는 것은 그 가림막 없이도 동작하는가다.
 */
@JpaSliceTest
@Import({ApiKeyService.class, ApiKeyRepositoryImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("UG-335: 트랜잭션 밖 지연 로딩 경계")
class ApiKeyLazyBoundarySliceTest {

    private static final String KEY = "gate_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final long OWNER = 100L;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private ApiKeyRepositoryImpl apiKeyRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(status -> {
            Project project = Project.builder()
                    .accountId(OWNER)
                    .projectName("테스트")
                    .branchName("branch-osiv")
                    .isDeleted(false)
                    .status(ProjectStatus.ACTIVE)
                    .build();
            em.persist(project);

            em.persist(ApiKey.builder()
                    .project(project)
                    .apiKey(KEY)
                    .secretKey("secret-osiv")
                    .isActive(true)
                    .issuedAt(LocalDateTime.now(ZoneOffset.UTC))
                    .build());
        });
    }

    /**
     * 트랜잭션이 없으므로 롤백도 없다 — 직접 지운다.
     *
     * <p>{@code api_key} 에 유니크 제약이 있어, 남겨 두면 다음 테스트의 저장이 실패한다.
     */
    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> {
            em.createQuery("DELETE FROM ApiKey k WHERE k.apiKey = :key")
                    .setParameter("key", KEY).executeUpdate();
            em.createQuery("DELETE FROM Project p WHERE p.branchName = :br")
                    .setParameter("br", "branch-osiv").executeUpdate();
        });
    }

    /**
     * 가드가 지키려는 조건이 실재하는지 — 대조군.
     *
     * <p>리포지토리를 직접 부르면 연관은 프록시인 채로 나오고, 트랜잭션 밖에서 건드리면
     * 터진다. 이것이 성립하지 않으면(예: 연관이 EAGER 로 바뀌면) 아래 테스트는 아무것도
     * 증명하지 못한 채 통과한다.
     */
    @Test
    @DisplayName("리포지토리를 직접 부르면 트랜잭션 밖에서 프록시를 못 읽는다")
    void 리포지토리_직접_조회는_프록시로_나온다() {
        ApiKey found = apiKeyRepository.findActiveByApiKeyWithLiveProject(KEY).orElseThrow();

        assertThat(Hibernate.isInitialized(found.getProject()))
                .as("연관이 EAGER 로 바뀌면 이 테스트 전체가 의미를 잃는다")
                .isFalse();

        assertThatThrownBy(() -> found.getProject().getAccountId())
                .as("트랜잭션도 요청 스코프 컨텍스트도 없으면 지연 로딩은 실패한다. "
                        + "이것이 UG-335 가 드러낸 조건이다")
                .isInstanceOf(LazyInitializationException.class);
    }

    /**
     * <b>데모 경로도 같아야 한다</b> (UG-293 델타 리뷰가 잡은 회귀).
     *
     * <p>데모는 소유 검증을 하지 않는다 — 대조할 accountId 가 없기 때문이다. 그래서 인증
     * 경로에서 프록시를 초기화해 주던 {@code validateOwnership} 이 불리지 않는다.
     *
     * <p>UG-293 이 매칭 유스케이스들의 {@code @Transactional} 을 떼자(커넥션 두 개를 동시에
     * 쥐는 문제 때문에) 이 경로에 방어막이 하나도 남지 않았고, {@code /api/v1/demo} 의 매칭
     * API 다섯 개가 전부 {@code LazyInitializationException} 으로 500 이 됐다. 그것도
     * 이력 행을 저장한 <b>뒤</b>라, 요청마다 사유 없는 행이 쌓였다.
     *
     * <p>이 테스트가 없어서 전체 빌드가 초록이었다. 여기 한 줄이면 잡혔을 것이다.
     */
    @Test
    @DisplayName("데모 경로도 트랜잭션 밖에서 프로젝트를 읽을 수 있다 — 소유 검증이 없어도")
    void 데모_경로도_초기화된_엔티티를_준다() {
        ApiKey found = apiKeyService.findByApiKeyUnverified(KEY);

        assertThat(Hibernate.isInitialized(found.getProject()))
                .as("데모는 소유 검증을 하지 않으므로 이 클래스가 직접 초기화해 줘야 한다")
                .isTrue();

        // 매칭 유스케이스들이 실제로 읽는 값. id 가 아니라 스칼라 필드여야 의미가 있다 —
        // id 는 프록시가 초기화 없이도 돌려준다.
        assertThat(found.getProject().getBranchName()).isEqualTo("branch-osiv");
        assertThat(found.getProject().getAccountId()).isEqualTo(OWNER);
    }

    /**
     * 본 검증. 서비스를 거치면 트랜잭션 밖에서도 읽을 수 있어야 한다.
     *
     * <p>{@code ApiKeyService} 가 자기 트랜잭션을 열고 소유 검증에서 프록시를 초기화하므로,
     * 반환된 엔티티는 컨텍스트가 닫힌 뒤에도 읽을 수 있다. 클래스의
     * {@code @Transactional(readOnly = true)} 를 떼면 이 테스트가 깨진다.
     */
    @Test
    @DisplayName("서비스를 거치면 트랜잭션 밖에서도 프로젝트를 읽는다")
    void 서비스는_초기화된_엔티티를_준다() {
        ApiKey found = apiKeyService.findOwnedByApiKey(KEY, OWNER);

        assertThat(Hibernate.isInitialized(found.getProject()))
                .as("소유 검증이 getAccountId() 로 프록시를 초기화해 둔다")
                .isTrue();

        // ExtractUseCase 와 GetFeatureListUseCase 가 실제로 하는 일. 둘 다 트랜잭션이 없다.
        assertThat(found.getProject().getAccountId()).isEqualTo(OWNER);
        assertThat(found.getProject().getId()).isNotNull();
        assertThat(found.getProject().isDeleted()).isFalse();
    }
}

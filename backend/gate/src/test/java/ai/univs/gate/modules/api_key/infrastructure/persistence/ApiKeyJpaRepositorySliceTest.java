package ai.univs.gate.modules.api_key.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.support.jpa.JpaSliceTest;
import ai.univs.gate.support.jpa.StubAuditorAware;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * API 키 리포지토리의 실제 쿼리 (UG-300 첫 사례).
 *
 * <p>이 클래스가 이 프로젝트의 <b>첫 스프링 컨텍스트 테스트</b>다. 그래서 두 가지를 겸한다 —
 * 슬라이스 인프라가 실제로 동작하는지 보이는 것과, 그동안 어떤 테스트도 닿지 못하던 파생
 * 쿼리를 덮는 것.
 *
 * <p><b>왜 하필 이 리포지토리인가.</b> 여기 걸린 것들이 보안·장애와 직결된다.
 * <ul>
 *   <li>{@code findByApiKeyAndIsActive} — 모든 인증 경로의 입구다. 속성 경로 오타 하나가
 *       기동 시점까지 숨는다.
 *   <li>{@code findByProjectIdAndIsActive} vs {@code findAllByProjectIdAndIsActive} — UG-288 이
 *       후자를 새로 만든 이유가 전자가 활성 키 2개에서 터진다는 것이었는데,
 *       <b>그 사실을 증명할 방법이 없었다.</b> 여기서 증명한다.
 * </ul>
 *
 * <p><b>여기서 프로덕션 코드를 바꾸지 않았다.</b> 티켓 항목 4는 UG-288 의 삭제 검사를 쿼리로
 * 옮기는 것을 첫 사례로 제안하지만, 그 판단은 "그때 판단" 으로 열려 있었다. 아래
 * {@link 삭제된_프로젝트}가 현재 쿼리의 실제 동작을 못박아 두므로, 옮길지 말지는 이제 데이터를
 * 보고 정할 수 있다. 인프라를 들이는 커밋에서 보안 통제까지 함께 옮기면 회귀 원인을 가리게 된다.
 */
@JpaSliceTest
@DisplayName("UG-300: API 키 리포지토리 슬라이스")
class ApiKeyJpaRepositorySliceTest {

    private static final String KEY = "gate_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Autowired
    private ApiKeyJpaRepository apiKeyJpaRepository;

    @Autowired
    private EntityManager em;

    private Project project;

    @BeforeEach
    void setUp() {
        project = 프로젝트를_저장한다("branch-1", false);
    }

    private Project 프로젝트를_저장한다(String branchName, boolean deleted) {
        Project p = Project.builder()
                .accountId(100L)
                .projectName("테스트")
                .branchName(branchName)
                .isDeleted(deleted)
                .status(deleted ? ProjectStatus.DELETED : ProjectStatus.ACTIVE)
                .build();
        em.persist(p);
        return p;
    }

    private ApiKey 키를_저장한다(Project owner, String key, boolean active) {
        ApiKey apiKey = ApiKey.builder()
                .project(owner)
                .apiKey(key)
                .secretKey("secret-" + key)
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC))
                .isActive(active)
                .build();
        em.persist(apiKey);
        return apiKey;
    }

    /** 영속성 컨텍스트를 비워 <b>실제 SELECT</b> 가 나가게 한다. 안 비우면 1차 캐시가 답한다. */
    private void 반영하고_비운다() {
        em.flush();
        em.clear();
    }

    @Nested
    @DisplayName("인프라가 실제로 동작한다")
    class 인프라 {

        /**
         * 이 테스트가 깨지면 아래 전부가 의미를 잃는다 — 감사 컬럼이 안 채워지면
         * {@code NOT NULL} 위반으로 저장 자체가 실패하기 때문이다.
         */
        @Test
        @DisplayName("JPA Auditing 이 붙어 생성 시각·생성자를 채운다")
        void 감사_컬럼이_채워진다() {
            ApiKey saved = 키를_저장한다(project, KEY, true);
            반영하고_비운다();

            ApiKey found = apiKeyJpaRepository.findById(saved.getId()).orElseThrow();

            assertThat(found.getCreatedAt()).isNotNull();
            assertThat(project.getCreatedBy())
                    .as("JpaConfig 의 auditorAwareRef 가 StubAuditorAware 를 찾아야 한다")
                    .isEqualTo(StubAuditorAware.감사자);
        }

        /**
         * LAZY 연관이 실제로 지연 로딩되는지.
         *
         * <p>{@code ApiKeyService.validateProjectNotDeleted} 가 {@code getProject().isDeleted()}
         * 로 프록시를 초기화하는 것에 의존한다. 그 전제가 여기서 확인된다.
         */
        @Test
        @DisplayName("project 연관이 LAZY 로 실려 나중에 초기화된다")
        void 지연로딩이_동작한다() {
            키를_저장한다(project, KEY, true);
            반영하고_비운다();

            ApiKey found = apiKeyJpaRepository.findByApiKeyAndIsActive(KEY, true).orElseThrow();

            // 초판은 getBranchName() 이 나오는지만 봤는데, 그건 EAGER 여도 통과한다
            // (변이 심기로 확인). 지연 '여부' 를 보려면 초기화 상태를 직접 물어야 한다.
            assertThat(Hibernate.isInitialized(found.getProject()))
                    .as("LAZY 라면 아직 프록시여야 한다. EAGER 로 바뀌면 여기서 깨진다")
                    .isFalse();

            assertThat(found.getProject().getBranchName()).isEqualTo("branch-1");

            assertThat(Hibernate.isInitialized(found.getProject()))
                    .as("필드를 읽는 순간 초기화된다 — validateProjectNotDeleted 가 기대는 동작이다")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("findByApiKeyAndIsActive — 모든 인증 경로의 입구")
    class 키로_조회 {

        @Test
        @DisplayName("활성 키를 찾는다")
        void 활성키를_찾는다() {
            키를_저장한다(project, KEY, true);
            반영하고_비운다();

            assertThat(apiKeyJpaRepository.findByApiKeyAndIsActive(KEY, true))
                    .isPresent()
                    .get()
                    .extracting(ApiKey::getApiKey)
                    .isEqualTo(KEY);
        }

        /**
         * 비활성 키가 걸러지는지. UG-288 이 프로젝트 삭제 시 키를 비활성화하도록 고쳤으므로,
         * 이 조건이 삭제된 프로젝트의 키를 막는 <b>1차 방어선</b>이다.
         */
        @Test
        @DisplayName("비활성 키는 찾지 않는다")
        void 비활성키는_안_나온다() {
            키를_저장한다(project, KEY, false);
            반영하고_비운다();

            assertThat(apiKeyJpaRepository.findByApiKeyAndIsActive(KEY, true)).isEmpty();
        }

        @Test
        @DisplayName("없는 키는 빈 결과다")
        void 없는키() {
            반영하고_비운다();

            assertThat(apiKeyJpaRepository.findByApiKeyAndIsActive("없는키", true)).isEmpty();
        }
    }

    /**
     * 활성 키가 2개인 상태를 <b>실제 DB 에서</b> 만들어 두 티켓의 결론을 확인한다.
     *
     * <p>UG-288 은 삭제 경로에 {@code findAllActiveByProjectId} 를 새로 만들었다. 티켓은
     * "{@code Optional} 조회는 활성 행이 2개면 {@code IncorrectResultSizeDataAccessException}
     * 을 던진다" 고 적었지만 그것을 확인할 테스트를 쓸 수 없었다.
     *
     * <p>UG-302 는 그 {@code Optional} 파생 쿼리를 <b>아예 지우고</b> 정렬된 목록 조회로
     * 바꿨다 — 예외가 나는 것 자체가 문제였기 때문이다(상세 조회와 재발급이 둘 다 500 이
     * 되고, 재발급이 막히면 그 프로젝트는 고칠 수단을 잃는다). 그래서 여기서는 "예외가
     * 난다" 가 아니라 <b>"예외 없이 최신 키가 나온다"</b> 를 확인한다.
     *
     * <p>이 파일이 머지되는 시점에는 UG-302 가 이미 dev 에 들어와 있어, 원래 있던
     * {@code 옵셔널은_터진다} 테스트는 컴파일되지 않는다. 지운 게 아니라 <b>같은 상황을
     * 반대 방향에서</b> 못박도록 바꿨다.
     */
    @Nested
    @DisplayName("활성 키가 2개일 때 (UG-288 / UG-302)")
    class 활성키_중복 {

        private Long 먼저_발급된_키;
        private Long 나중_발급된_키;

        @BeforeEach
        void 활성키_둘() {
            먼저_발급된_키 = 키를_저장한다(project, KEY, true).getId();
            나중_발급된_키 =
                    키를_저장한다(project, "gate_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", true).getId();
            반영하고_비운다();
        }

        /**
         * UG-302 가 그 예외를 없앴다 — <b>진짜 DB 로</b> 확인한다.
         *
         * <p>예전에는 여기서 {@code findByProjectIdAndIsActive}({@code Optional}) 가
         * {@code IncorrectResultSizeDataAccessException} 으로 터졌고, 그래서 프로젝트 상세
         * 조회와 키 재발급이 둘 다 500 이 됐다. 재발급이 막히면 그 프로젝트는 스스로 빠져나올
         * 수단을 잃는다 — 상태를 고칠 유일한 방법이 그 상태 때문에 막히기 때문이다.
         *
         * <p>UG-302 는 그 파생 쿼리를 아예 지우고 정렬된 목록 조회로 바꿨다. 이 테스트는
         * 그것이 <b>실제 데이터베이스에서</b> 예외 없이 최신 키를 돌려주는지 본다 — 목 기반
         * 단위 테스트는 스프링 데이터가 언제 그 예외를 던지는지 재현할 수 없다.
         */
        @Test
        @DisplayName("정렬 목록 조회는 터지지 않고 최신 키를 먼저 준다 (UG-302)")
        void 목록조회는_최신을_먼저_준다() {
            List<ApiKey> found = apiKeyJpaRepository
                    .findAllByProjectIdAndIsActiveOrderByIssuedAtDescIdDesc(project.getId(), true);

            assertThat(found).hasSize(2);
            assertThat(found.get(0).getId())
                    .as("두 키의 issued_at 이 같으므로 id DESC 가 순서를 정한다 — "
                            + "이 2차 정렬이 없으면 '가장 최근' 이 호출마다 달라질 수 있다")
                    .isEqualTo(나중_발급된_키);
            assertThat(found.get(1).getId()).isEqualTo(먼저_발급된_키);
        }

        @Test
        @DisplayName("List 반환 쿼리는 둘 다 돌려준다 — 삭제 경로가 전부 끌 수 있다")
        void 리스트는_전부_돌려준다() {
            List<ApiKey> found =
                    apiKeyJpaRepository.findAllByProjectIdAndIsActive(project.getId(), true);

            assertThat(found).hasSize(2);
        }

        /**
         * <b>이 슬라이스는 부분 유니크 인덱스를 보지 못한다</b> — 그 사실 자체를 기록해 둔다.
         *
         * <p>초판 javadoc 은 "UG-302 가 인덱스를 넣으면 이 테스트가 깨지면서 알려 준다" 고
         * 적었는데 <b>틀렸다.</b> 인덱스는 V23 마이그레이션에 들어갔지만, 이 슬라이스의 스키마는
         * 마이그레이션이 아니라 <b>엔티티</b>에서 만들어진다({@code ddl-auto: create-drop}).
         * 그래서 인덱스가 생긴 뒤에도 여기서는 두 행이 그대로 들어간다.
         *
         * <p>JPA 로 표현할 수도 없다. {@code @Table(uniqueConstraints)} 로
         * {@code (project_id, is_active)} 를 걸면 <b>비활성 키도 프로젝트당 하나</b>가 되어
         * 이력이 쌓이지 않는다 — 부분 인덱스와 전혀 다른 제약이다.
         *
         * <p>그래서 이 테스트가 지키는 것은 "제약이 없다" 가 아니라 <b>"이 하네스로는 제약을
         * 검증할 수 없다"</b> 는 경계다. 실제 인덱스 동작 확인은 Testcontainers 로 진짜
         * PostgreSQL·오라클을 띄워야 한다 ({@code JpaSliceTest} javadoc 참고).
         */
        @Test
        @DisplayName("이 하네스는 V23 부분 유니크 인덱스를 검증하지 못한다 — 경계 기록")
        void 슬라이스는_인덱스를_보지_못한다() {
            assertThat(apiKeyJpaRepository.findAllByProjectIdAndIsActive(project.getId(), true))
                    .as("엔티티에서 만든 스키마라 마이그레이션의 인덱스가 없다. 여기가 초록이어도 "
                            + "운영 DB 에서 중복이 허용된다는 뜻이 아니다")
                    .hasSize(2);
        }
    }

    /**
     * 삭제된 프로젝트의 키를 <b>쿼리가 걸러내지 않는다</b>는 현재 동작을 못박는다.
     *
     * <p>UG-288 은 그 검사를 {@code ApiKeyService.validateProjectNotDeleted} 라는 자바 조건으로
     * 뒀다 — 쿼리에 조건을 붙이는 편이 나았지만 그것을 검증할 테스트가 없었기 때문이다.
     * 이 인프라가 생겼으니 이제 옮길 수 있다.
     *
     * <p>이 커밋에서 옮기지 않은 이유: 자바 조건은 WARN 로그를 남긴다("정상 사용에서는 나올 수
     * 없는 조합"). 쿼리로 옮기면 그 관측이 사라진다. 무엇을 잃고 무엇을 얻는지는 별도 판단이고,
     * 인프라를 들이는 커밋에서 보안 통제까지 함께 옮기면 회귀 원인을 가린다.
     *
     * <p>옮기게 되면 이 테스트가 <b>깨지면서</b> 알려 준다.
     */
    @Nested
    @DisplayName("삭제된 프로젝트의 키 (UG-288 후속 판단 근거)")
    class 삭제된_프로젝트 {

        @Test
        @DisplayName("쿼리는 삭제 여부를 보지 않는다 — 지금은 자바 조건이 막는다")
        void 쿼리는_거르지_않는다() {
            Project deleted = 프로젝트를_저장한다("branch-deleted", true);
            키를_저장한다(deleted, KEY, true);
            반영하고_비운다();

            Optional<ApiKey> found = apiKeyJpaRepository.findByApiKeyAndIsActive(KEY, true);

            assertThat(found)
                    .as("쿼리로 옮기면 여기가 비게 된다. 그때 이 테스트를 함께 뒤집을 것")
                    .isPresent();
            assertThat(found.get().getProject().isDeleted()).isTrue();
        }
    }
}

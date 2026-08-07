package ai.univs.gate.modules.api_key.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 어댑터가 {@code is_active} 를 어느 값으로 넘기는지 못박는다 (UG-288 델타 리뷰).
 *
 * <p>이 클래스는 테스트가 하나도 없었다. 그래서
 * {@code findAllByProjectIdAndIsActive(projectId, true)} 를 {@code false} 로 바꿔도 전 테스트가
 * 초록이었다. 그 변이는 <b>UG-288 의 원래 증상을 그대로 복원한다</b> — 삭제할 때 이미 꺼진 키만
 * 다시 끄고 살아 있는 키는 그대로 두므로, 삭제된 프로젝트의 키로 등록·매칭이 계속 된다.
 *
 * <p>메서드 이름이 {@code ...AndIsActive(id, boolean)} 라 참/거짓 어느 쪽도 컴파일된다는 점이
 * 위험하다. 이름이 {@code ...AndIsActiveTrue} 였다면 애초에 불가능한 변이다.
 *
 * <p>쿼리가 실제로 그 행을 가져오는지는 여기서 볼 수 없다 — 그건 JPA 슬라이스 테스트의 몫이다
 * (UG-300). 여기서 지키는 것은 "어느 인자로 물어보는가" 까지다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-288: API 키 리포지토리 어댑터")
class ApiKeyRepositoryImplTest {

    private static final long PROJECT = 42L;

    @Mock
    private ApiKeyJpaRepository apiKeyJpaRepository;

    @InjectMocks
    private ApiKeyRepositoryImpl apiKeyRepositoryImpl;

    @Test
    @DisplayName("활성 키 전체 조회는 is_active = true 로 묻는다")
    void 활성_전체조회는_true로_묻는다() {
        ApiKey 활성키 = ApiKey.builder().isActive(true).build();
        given(apiKeyJpaRepository.findAllByProjectIdAndIsActive(PROJECT, true))
                .willReturn(List.of(활성키));

        assertThat(apiKeyRepositoryImpl.findAllActiveByProjectId(PROJECT)).containsExactly(활성키);
    }

    @Test
    @DisplayName("활성 키 단건 조회도 is_active = true 로 묻는다")
    void 활성_단건조회도_true로_묻는다() {
        ApiKey 활성키 = ApiKey.builder().isActive(true).build();
        given(apiKeyJpaRepository.findAllByProjectIdAndIsActiveOrderByIssuedAtDescIdDesc(
                PROJECT, true)).willReturn(List.of(활성키));

        assertThat(apiKeyRepositoryImpl.findLatestActiveByProjectId(PROJECT)).contains(활성키);
    }

    /**
     * 활성 키가 2개여도 예외가 아니라 <b>가장 최근 것</b>이 나온다 (UG-302).
     *
     * <p>예전에는 {@code Optional} 파생 쿼리라 이 상황에서
     * {@code IncorrectResultSizeDataAccessException} 이 났고, 그러면 프로젝트 상세 조회와 키
     * 재발급이 둘 다 500 이 됐다 — 상태를 고칠 유일한 수단인 재발급이 그 상태 때문에 막혔다.
     */
    @Test
    @DisplayName("활성 키가 2개면 예외 대신 가장 최근 것을 돌려준다")
    void 활성_두개면_최신을_고른다() {
        ApiKey 최신 = ApiKey.builder().id(2L).isActive(true).build();
        ApiKey 옛것 = ApiKey.builder().id(1L).isActive(true).build();
        given(apiKeyJpaRepository.findAllByProjectIdAndIsActiveOrderByIssuedAtDescIdDesc(
                PROJECT, true)).willReturn(List.of(최신, 옛것));

        assertThat(apiKeyRepositoryImpl.findLatestActiveByProjectId(PROJECT))
                .as("여기서 예외가 나면 그 프로젝트는 상세 조회도 재발급도 못 하게 된다")
                .contains(최신);
    }

    @Test
    @DisplayName("활성 키가 없으면 비어 있다")
    void 활성이_없으면_비어있다() {
        given(apiKeyJpaRepository.findAllByProjectIdAndIsActiveOrderByIssuedAtDescIdDesc(
                PROJECT, true)).willReturn(List.of());

        assertThat(apiKeyRepositoryImpl.findLatestActiveByProjectId(PROJECT)).isEmpty();
    }

    @Test
    @DisplayName("키 문자열 조회도 is_active = true 로 묻는다")
    void 키_문자열_조회도_true로_묻는다() {
        ApiKey 활성키 = ApiKey.builder().isActive(true).build();
        given(apiKeyJpaRepository.findByApiKeyAndIsActive("univs_live_abcdefghijklmnop", true))
                .willReturn(Optional.of(활성키));

        assertThat(apiKeyRepositoryImpl.findByApiKeyAndIsActiveTrue("univs_live_abcdefghijklmnop"))
                .contains(활성키);
    }

    /**
     * "조용히 고르지 않는다" 가 이 설계의 전제다 (반박 리뷰 지적).
     *
     * <p>티켓은 "가장 최근 것을 조용히 고르는 것은 문제를 숨기는 쪽이라 권하지 않는다" 고 적었다.
     * 그 권고를 상쇄하는 근거가 ERROR 로그인데, 정작 <b>그 로그만 무방비였다</b> — 지우는 변이도
     * 임계값을 {@code > 2} 로 미루는 변이도 전부 통과했다.
     */
    @Test
    @DisplayName("활성 키가 2개면 ERROR 로 남긴다 — 조용히 고르지 않는다")
    void 중복은_ERROR_로_남는다() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(ApiKeyRepositoryImpl.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            given(apiKeyJpaRepository.findAllByProjectIdAndIsActiveOrderByIssuedAtDescIdDesc(
                    PROJECT, true)).willReturn(List.of(
                            ApiKey.builder().id(2L).isActive(true).build(),
                            ApiKey.builder().id(1L).isActive(true).build()));

            apiKeyRepositoryImpl.findLatestActiveByProjectId(PROJECT);

            assertThat(appender.list)
                    .as("로그가 없으면 어긋난 데이터가 어디에도 기록되지 않는다 — 그러면 티켓이 "
                            + "반대한 '조용히 하나를 고르는' 동작 그대로다")
                    .anySatisfy(event -> {
                        assertThat(event.getLevel())
                                .isEqualTo(ch.qos.logback.classic.Level.ERROR);
                        assertThat(event.getFormattedMessage())
                                .contains(String.valueOf(PROJECT))
                                .contains("2");
                    });
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("활성 키가 1개면 아무 로그도 남기지 않는다 — 임계값이 밀리면 중복을 놓친다")
    void 정상일_때는_조용하다() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(ApiKeyRepositoryImpl.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            given(apiKeyJpaRepository.findAllByProjectIdAndIsActiveOrderByIssuedAtDescIdDesc(
                    PROJECT, true)).willReturn(
                            List.of(ApiKey.builder().id(1L).isActive(true).build()));

            apiKeyRepositoryImpl.findLatestActiveByProjectId(PROJECT);

            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
        }
    }

    /**
     * 정렬이 <b>내림차순</b>인지 (반박 리뷰 지적).
     *
     * <p>DESC 는 파생 쿼리 <b>메서드 이름에만</b> 존재한다. 목 테스트는 이름을 그대로 스텁하므로
     * {@code Asc} 로 바꾸는 변이가 기계적 리네임만으로 통과했다. 스프링 데이터의 실제 파서
     * ({@code PartTree})에 이름을 넣어 나오는 {@code Sort} 를 본다 — 기대값은 이름이 아니라
     * <b>의도</b>(최신 우선)에서 나오므로 동어반복이 아니다.
     */
    @Test
    @DisplayName("활성 키 목록은 최신 우선으로 정렬된다 — issuedAt DESC, id DESC")
    void 최신_우선으로_정렬한다() {
        String methodName = java.util.Arrays.stream(ApiKeyJpaRepository.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(n -> n.contains("OrderBy"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("OrderBy 파생 쿼리가 사라졌다"));

        org.springframework.data.domain.Sort sort =
                new org.springframework.data.repository.query.parser.PartTree(
                        methodName, ApiKey.class).getSort();

        assertThat(sort.getOrderFor("issuedAt"))
                .as("오름차순이면 '가장 최근' 이 가장 오래된 키가 된다")
                .isNotNull()
                .extracting(org.springframework.data.domain.Sort.Order::isDescending)
                .isEqualTo(true);
        assertThat(sort.getOrderFor("id"))
                .as("같은 초에 발급된 두 키의 순서가 정해지지 않으면 '가장 최근' 이 호출마다 달라진다")
                .isNotNull()
                .extracting(org.springframework.data.domain.Sort.Order::isDescending)
                .isEqualTo(true);
    }
}

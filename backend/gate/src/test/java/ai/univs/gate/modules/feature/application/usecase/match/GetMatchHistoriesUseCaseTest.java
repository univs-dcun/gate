package ai.univs.gate.modules.feature.application.usecase.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.result.match.MatchHistoriesResult;
import ai.univs.gate.modules.feature.application.result.match.MatchHistoryResult;
import ai.univs.gate.modules.feature.domain.entity.ActivityLog;
import ai.univs.gate.modules.feature.domain.enums.ActivitySource;
import ai.univs.gate.modules.feature.domain.enums.ActivityType;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.ActivityLogRepository;
import ai.univs.gate.modules.feature.infrastructure.persistence.query.MatchHistoryQuery;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * UG-326: 통합 이력 유스케이스 두 개 — 매핑(source 포함)·전체 건수의 모집단·미존재 처리.
 *
 * <p>SQL 은 {@code ActivityLogSliceTest} 가 지킨다. 여기서는 유스케이스가 리포지토리 결과를 응답 형태로
 * 옮길 때 무엇을 잃는지 본다. PIT 가 두 유스케이스의 반환값 null 뮤턴트를 생존시켰다 — 테스트가 없었다.
 *
 * <p>유스케이스는 생성자로 직접 조립한다. {@code @Nested} 안의 {@code @InjectMocks} 는 바깥 클래스의
 * {@code @Mock} 을 받지 못한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-326: 통합 이력 유스케이스")
class GetMatchHistoriesUseCaseTest {

    private static final Long ACCOUNT_ID = 10L;
    private static final String API_KEY = "gate_test-api-key";
    private static final String TX = "550e8400-e29b-41d4-a716-446655440000";

    @Mock private ActivityLogRepository activityLogRepository;
    @Mock private ApiKeyService apiKeyService;
    @Mock private FileService fileService;
    @Mock private ProjectSettingsService projectSettingsService;

    private Project project;
    private GetMatchHistoriesUseCase listUseCase;
    private GetMatchHistoryByTransactionUuidUseCase singleUseCase;

    @BeforeEach
    void setUp() {
        project = Project.builder().id(1L).accountId(ACCOUNT_ID).projectName("p").branchName("b")
                .status(ProjectStatus.ACTIVE).build();
        ApiKey apiKey = ApiKey.builder().id(5L).project(project).apiKey(API_KEY).secretKey("s")
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC)).isActive(true).build();
        given(apiKeyService.findOwnedByApiKey(API_KEY, ACCOUNT_ID)).willReturn(apiKey);
        // 미존재 경로에서는 여기까지 오지 않으므로 lenient — strict stubs 가 "안 쓴 스텁" 으로 실패시키지 않게.
        lenient().when(projectSettingsService.findByProject(project))
                .thenReturn(ProjectSettings.builder().id(2L).project(project).consentEnabled(true).build());
        lenient().when(fileService.getFileServerPath()).thenReturn("https://files/");

        listUseCase = new GetMatchHistoriesUseCase(activityLogRepository, apiKeyService, fileService, projectSettingsService);
        singleUseCase = new GetMatchHistoryByTransactionUuidUseCase(activityLogRepository, apiKeyService, fileService, projectSettingsService);
    }

    /** {@code ActivityLog} 는 읽기 전용(생성자 없음)이라 mock 으로 대신한다 — 값만 필요하다. 스텁 밖에서 만들 것. */
    private static ActivityLog 행(ActivitySource source, ActivityType type, Long sourceId, BigDecimal similarity) {
        ActivityLog l = mock(ActivityLog.class);
        given(l.getSourceId()).willReturn(sourceId);
        given(l.getId()).willReturn(sourceId + 1000);   // 공유 시퀀스 (UG-328)
        given(l.getProjectId()).willReturn(1L);
        given(l.getFeatureType()).willReturn(FeatureType.FACE);
        given(l.getActivityType()).willReturn(type);
        given(l.getSource()).willReturn(source);
        given(l.getEventTime()).willReturn(LocalDateTime.of(2026, 9, 21, 1, 0));
        given(l.getCheckLiveness()).willReturn(false);
        given(l.getSuccess()).willReturn(true);
        given(l.getFeatureId()).willReturn("fid");
        given(l.getFeatureSeq()).willReturn(7L);
        given(l.getUserDescription()).willReturn("홍길동");
        given(l.getSimilarity()).willReturn(similarity);
        given(l.getFeatureImagePath()).willReturn("feature/a.jpg");
        given(l.getMatchedFeatureImagePath()).willReturn(null);
        given(l.getFailureType()).willReturn(null);
        given(l.getTransactionUuid()).willReturn(TX);
        given(l.getConsentSnapshot()).willReturn(true);
        given(l.getCreatedAt()).willReturn(LocalDateTime.of(2026, 9, 21, 1, 0));
        return l;
    }

    private static MatchHistoryQuery 조회(boolean includeDeletions) {
        return new MatchHistoryQuery(ACCOUNT_ID, API_KEY, null, "ALL", "ALL", "ALL", 1, 10,
                false, null, null, "DESC", "identifyTime", includeDeletions);
    }

    @Test
    @DisplayName("목록: 두 출처의 행을 같은 형태로 옮기고 source·일련번호·유사도(null 허용)를 보존한다")
    void 목록_매핑() {
        ActivityLog match = 행(ActivitySource.MATCH, ActivityType.IDENTIFY, 100L, new BigDecimal("88.50"));
        ActivityLog feat  = 행(ActivitySource.FEATURE, ActivityType.DELETE, 3L, null);
        MatchHistoryQuery q = 조회(true);
        given(activityLogRepository.findAllByQuery(q, 1L)).willReturn(new PageImpl<>(List.of(match, feat), PageRequest.of(0, 10), 2));
        given(activityLogRepository.countByProjectId(1L, true)).willReturn(42L);

        MatchHistoriesResult result = listUseCase.execute(q);

        assertThat(result).isNotNull();
        assertThat(result.results()).hasSize(2);
        MatchHistoryResult r0 = result.results().get(0);
        assertThat(r0.source()).isEqualTo(ActivitySource.MATCH);
        assertThat(r0.matchType()).isEqualTo(ActivityType.IDENTIFY);
        assertThat(r0.matchingHistoryId()).isEqualTo(100L);
        assertThat(r0.sequence()).as("UG-328: 일련번호 = 공유 시퀀스").isEqualTo(1100L);
        assertThat(r0.similarity()).isEqualByComparingTo("88.50");
        assertThat(r0.featureImagePath()).as("동의 ON 이면 파일 서버 경로가 앞에 붙는다").startsWith("https://files/");
        MatchHistoryResult r1 = result.results().get(1);
        assertThat(r1.source()).isEqualTo(ActivitySource.FEATURE);
        assertThat(r1.matchType()).isEqualTo(ActivityType.DELETE);
        assertThat(r1.matchingHistoryId()).isEqualTo(3L);
        assertThat(r1.sequence()).isEqualTo(1003L);
        assertThat(r1.similarity()).isNull();
        assertThat(result.page().totalCount()).as("전체 건수는 목록과 같은 모집단(삭제 포함)").isEqualTo(42L);
    }

    @Test
    @DisplayName("목록: includeDeletions=false 면 전체 건수도 삭제를 뺀 모집단으로 센다")
    void 목록_전체건수_모집단() {
        MatchHistoryQuery q = 조회(false);
        given(activityLogRepository.findAllByQuery(q, 1L)).willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        given(activityLogRepository.countByProjectId(1L, false)).willReturn(7L);

        assertThat(listUseCase.execute(q).page().totalCount()).isEqualTo(7L);
    }

    @Test
    @DisplayName("단건: 등록·삭제 uuid 도 찾아 source=FEATURE 로 돌려준다")
    void 단건_특징점_사건도_찾는다() {
        ActivityLog row = 행(ActivitySource.FEATURE, ActivityType.REGISTER, 9L, null);
        given(activityLogRepository.findLatestByProjectIdAndTransactionUuid(1L, TX)).willReturn(Optional.of(row));

        MatchHistoryResult r = singleUseCase.execute(ACCOUNT_ID, API_KEY, TX);

        assertThat(r).isNotNull();
        assertThat(r.source()).isEqualTo(ActivitySource.FEATURE);
        assertThat(r.matchType()).isEqualTo(ActivityType.REGISTER);
        assertThat(r.matchingHistoryId()).isEqualTo(9L);
        assertThat(r.sequence()).isEqualTo(1009L);
        assertThat(r.transactionUuid()).isEqualTo(TX);
    }

    @Test
    @DisplayName("단건: 없으면 NOT_FOUND_MATCHING_HISTORY")
    void 단건_없음() {
        given(activityLogRepository.findLatestByProjectIdAndTransactionUuid(1L, TX)).willReturn(Optional.empty());

        assertThatThrownBy(() -> singleUseCase.execute(ACCOUNT_ID, API_KEY, TX))
                .isInstanceOf(CustomGateException.class)
                .satisfies(e -> assertThat(((CustomGateException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND_MATCHING_HISTORY));
    }
}

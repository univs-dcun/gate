package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.support.history.HistoryRecorder;
import ai.univs.gate.modules.feature.application.input.face.IdentifyCandidatesByDescriptorInput;
import ai.univs.gate.modules.feature.application.result.face.IdentifyCandidatesByDescriptorResult;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.IdentifyCandidatesFaceFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.IdentifyCandidatesFaceFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.face.FaceService;
import ai.univs.gate.support.project.ProjectSettingsService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * UG-314 gate 쪽 계약을 지킨다.
 *
 * <p>gate 는 이 경로에서 세 가지 일을 한다.
 *
 * <ul>
 *   <li><b>스케일 변환.</b> 클라이언트는 백분율(0 초과 100 이하), 하위 서비스는 0.0 ~ 1.0 이다.
 *       경계에서 {@code BigDecimal} 로 나눈다 — 85.33 / 100 은 이진 부동소수점에서 정확하지
 *       않고, 유사도가 소수점 5자리 반올림 후 비교되는 경로라 경계 판정이 흔들린다.</li>
 *   <li><b>사용자 정보 결합.</b> face·match 는 {@code faceId} 만 안다. 설명 문구는 gate 에만
 *       있으므로 IN 절로 한 번에 붙인다.</li>
 *   <li><b>이력 한 행</b> (패턴 A). 대표값은 최상위 후보다.</li>
 * </ul>
 *
 * <p><b>가장 조심할 지점은 후보가 gate 에 없을 때다.</b> 한 명이 없다고 목록 전체를 실패시키면
 * 이 API 의 용도가 사라지고, 반대로 조용히 건너뛰면 두 저장소가 어긋난 사실이 영영 안 드러난다.
 * 그래서 <b>그 후보만 빼고 진행하되 WARN 을 남기고</b>, 하나도 못 찾으면 매칭 실패가 아니라
 * {@code INVALID_USER} 로 남긴다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-314: 특징점 기반 1:N 후보 목록 (gate)")
class IdentifyCandidatesByDescriptorUseCaseTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final Long SAVED_ID = 100L;
    private static final String API_KEY = "gate_test-api-key";
    private static final String BRANCH = "branch-uuid-1";
    private static final String TX = "550e8400-e29b-41d4-a716-446655440000";
    private static final String DESCRIPTOR = "descriptor-base64";

    @Mock private HistoryRecorder historyRecorder;
    @Mock private BiometricFeatureRepository biometricFeatureRepository;
    @Mock private ProjectSettingsService projectSettingsService;
    @Mock private ApiKeyService apiKeyService;
    @Mock private FaceService faceService;

    @InjectMocks private IdentifyCandidatesByDescriptorUseCase useCase;

    private Project project;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void 픽스처() {
        project = Project.builder()
                .id(PROJECT_ID)
                .accountId(ACCOUNT_ID)
                .projectName("gate-project")
                .branchName(BRANCH)
                .status(ProjectStatus.ACTIVE)
                .build();
        ApiKey apiKey = ApiKey.builder()
                .id(5L)
                .project(project)
                .apiKey(API_KEY)
                .secretKey("secret")
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC))
                .isActive(true)
                .build();

        lenient().when(apiKeyService.findOwnedByApiKey(API_KEY, ACCOUNT_ID)).thenReturn(apiKey);
        lenient().when(projectSettingsService.findByProject(project)).thenReturn(
                ProjectSettings.builder().id(2L).project(project).consentEnabled(true).build());
        lenient().when(historyRecorder.start(any(MatchHistory.class))).thenAnswer(invocation -> {
            MatchHistory saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", SAVED_ID);
            return saved;
        });

        logger = (Logger) LoggerFactory.getLogger(IdentifyCandidatesByDescriptorUseCase.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void 정리() {
        logger.detachAppender(appender);
        appender.stop();
    }

    private static IdentifyCandidatesByDescriptorInput 입력(String thresholdPercent, int maxCandidates) {
        return new IdentifyCandidatesByDescriptorInput(
                ACCOUNT_ID, API_KEY, DESCRIPTOR, new BigDecimal(thresholdPercent), maxCandidates, TX);
    }

    /** face 응답. faceId·similarity(도메인 스케일) 를 번갈아 넘긴다. */
    private void face가_돌려준다(String... faceIdAndSimilarity) {
        List<IdentifyCandidatesFaceFeignResponseDTO.Candidate> list = new ArrayList<>();
        for (int i = 0; i < faceIdAndSimilarity.length; i += 2) {
            list.add(new IdentifyCandidatesFaceFeignResponseDTO.Candidate(
                    faceIdAndSimilarity[i], faceIdAndSimilarity[i + 1]));
        }
        given(faceService.identifyCandidatesByDescriptor(any()))
                .willReturn(IdentifyCandidatesFaceFeignResponseDTO.builder()
                        .transactionUuid(TX)
                        .candidates(list)
                        // face 는 임계치 통과자가 없어도 최근접 유사도를 함께 준다.
                        .nearestSimilarity(list.isEmpty() ? null : list.getFirst().getSimilarity())
                        .threshold("0.85")
                        .result(!list.isEmpty())
                        .build());
    }

    /** 임계치 통과자는 없지만 근접자는 있었던 응답 (face 가 목록을 잘라 보낸 상태). */
    private void face가_통과자없이_근접만_알려준다(String nearestSimilarity) {
        given(faceService.identifyCandidatesByDescriptor(any()))
                .willReturn(IdentifyCandidatesFaceFeignResponseDTO.builder()
                        .transactionUuid(TX)
                        .candidates(List.of())
                        .nearestSimilarity(nearestSimilarity)
                        .threshold("0.85")
                        .result(false)
                        .build());
    }

    /** gate 에 살아 있는 특징점. featureId·설명 문구를 번갈아 넘긴다. */
    private void gate에_있다(String... featureIdAndDescription) {
        List<BiometricFeature> features = new ArrayList<>();
        for (int i = 0; i < featureIdAndDescription.length; i += 2) {
            features.add(BiometricFeature.builder()
                    .id((long) (i + 1))
                    .project(project)
                    .type(FeatureType.FACE)
                    .featureId(featureIdAndDescription[i])
                    .description(featureIdAndDescription[i + 1])
                    .isDeleted(false)
                    .build());
        }
        given(biometricFeatureRepository.findAllByFeatureIdInAndProjectIdAndTypeAndIsDeletedFalse(
                any(), any(), any())).willReturn(features);
    }

    private MatchHistory 저장된_이력() {
        ArgumentCaptor<MatchHistory> captor = ArgumentCaptor.forClass(MatchHistory.class);
        verify(historyRecorder).start(captor.capture());
        return captor.getValue();
    }

    private IdentifyCandidatesFaceFeignRequestDTO face로_보낸_요청() {
        ArgumentCaptor<IdentifyCandidatesFaceFeignRequestDTO> captor =
                ArgumentCaptor.forClass(IdentifyCandidatesFaceFeignRequestDTO.class);
        verify(faceService).identifyCandidatesByDescriptor(captor.capture());
        return captor.getValue();
    }

    private List<String> 경고들() {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("성공")
    class 성공 {

        @Test
        @DisplayName("후보를 유사도 순 그대로, 백분율과 사용자 설명을 붙여 반환한다")
        void 정상() {
            face가_돌려준다("face-a", "0.97000", "face-b", "0.88000");
            gate에_있다("face-a", "홍길동", "face-b", "김철수");

            IdentifyCandidatesByDescriptorResult result = useCase.execute(입력("85.00", 10));

            assertThat(result.success()).isTrue();
            assertThat(result.matchingHistoryId()).isEqualTo(SAVED_ID);
            assertThat(result.projectId()).isEqualTo(PROJECT_ID);
            assertThat(result.matchType()).isEqualTo(MatchType.IDENTIFY);
            assertThat(result.threshold())
                    .as("응답 임계값은 클라이언트가 보낸 백분율 그대로여야 한다")
                    .isEqualByComparingTo("85.00");
            assertThat(result.failureType()).isEmpty();
            assertThat(result.transactionUuid()).isEqualTo(TX);

            assertThat(result.candidates())
                    .extracting(IdentifyCandidatesByDescriptorResult.Candidate::featureId)
                    .containsExactly("face-a", "face-b");
            assertThat(result.candidates())
                    .extracting(IdentifyCandidatesByDescriptorResult.Candidate::userDescription)
                    .containsExactly("홍길동", "김철수");
            assertThat(result.candidates())
                    .extracting(IdentifyCandidatesByDescriptorResult.Candidate::similarity)
                    .as("응답 유사도는 백분율이다 — 기존 API 들과 같은 스케일이라야 클라이언트가 "
                            + "임계값과 나란히 놓고 판단할 수 있다")
                    .containsExactly(new BigDecimal("97.00"), new BigDecimal("88.00"));

            assertThat(경고들()).isEmpty();
        }

        @Test
        @DisplayName("이력은 한 행이고 대표값은 최상위 후보다")
        void 이력_대표값() {
            face가_돌려준다("face-a", "0.97000", "face-b", "0.88000");
            gate에_있다("face-a", "홍길동", "face-b", "김철수");

            useCase.execute(입력("85.00", 10));

            MatchHistory saved = 저장된_이력();
            assertThat(saved.getSuccess()).isTrue();
            assertThat(saved.getMatchType()).isEqualTo(MatchType.IDENTIFY);
            assertThat(saved.getFeatureType()).isEqualTo(FeatureType.FACE);
            assertThat(saved.getFeatureId())
                    .as("2등을 남기면 이력만 보고 '누가 매칭됐나' 를 답할 수 없게 된다")
                    .isEqualTo("face-a");
            assertThat(saved.getUserDescription()).isEqualTo("홍길동");
            assertThat(saved.getSimilarity())
                    .as("이력도 백분율로 저장된다 (MatchHistory.toPercent)")
                    .isEqualByComparingTo("97.00");
            assertThat(saved.getCheckLiveness())
                    .as("descriptor 경로는 라이브니스를 수행하지 않는다")
                    .isFalse();
            assertThat(saved.getConsentSnapshot()).isTrue();
        }

        @Test
        @DisplayName("유사도 반올림은 이력과 같은 규칙이다 (소수점 2자리 HALF_UP)")
        void 유사도_반올림() {
            face가_돌려준다("face-a", "0.973145");
            gate에_있다("face-a", "홍길동");

            IdentifyCandidatesByDescriptorResult result = useCase.execute(입력("85.00", 10));

            assertThat(result.candidates().getFirst().similarity())
                    .isEqualByComparingTo("97.31");
        }
    }

    @Nested
    @DisplayName("스케일 변환 (백분율 → 도메인)")
    class 스케일 {

        @Test
        @DisplayName("85.00 을 보내면 face 로 0.85 가 간다")
        void 정수_백분율() {
            face가_돌려준다("face-a", "0.97000");
            gate에_있다("face-a", "홍길동");

            useCase.execute(입력("85.00", 10));

            assertThat(face로_보낸_요청().getThreshold()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("소수점 백분율은 BigDecimal 로 나눈다 — double 나눗셈은 값이 어긋난다")
        void 소수점_백분율() {
            // 70.01 은 두 경로가 갈리고, 갈리는 방향이 실제로 해로운 값이다.
            //   BigDecimal("70.01").divide(100, 10, HALF_UP).doubleValue() → 0.7001
            //   70.01d / 100.0d                                            → 0.7001000000000001
            // double 쪽이 1 ULP 더 엄격해서, 유사도 "0.70010" 인 후보가 BigDecimal 경로에서는
            // 통과하고 double 경로에서는 탈락한다. 유사도가 5자리 반올림 후 비교되는 경로라
            // 경계에 정확히 걸린 사람이 조용히 빠진다.
            //
            // 값을 바꾸려면 두 가지를 확인할 것 (반박 리뷰 지적).
            //   1) 두 경로가 실제로 갈리는가 — 85.33 은 우연히 같은 double 이라 무의미하다
            //   2) 갈리는 방향이 double 이 더 엄격한 쪽인가 — 70.02 는 더 관대한 쪽이라
            //      아무도 빠지지 않는다. 70.00~100.00 구간에 해로운 값은 480개 있다
            face가_돌려준다("face-a", "0.97000");
            gate에_있다("face-a", "홍길동");

            useCase.execute(입력("70.01", 10));

            assertThat(face로_보낸_요청().getThreshold())
                    .as("double 로 나누면 0.7001000000000001 이 되어 face 로 더 엄격한 값이 "
                            + "넘어가고, 유사도 0.70010 인 후보가 조용히 빠진다")
                    .isEqualTo(0.7001);
        }

        @Test
        @DisplayName("상한 100 은 도메인 상한 1.0 으로 간다 — face 검증을 통과해야 한다")
        void 상한() {
            face가_돌려준다();

            useCase.execute(입력("100", 10));

            assertThat(face로_보낸_요청().getThreshold())
                    .as("1.0 을 넘겨 보내면 face 의 @DecimalMax('1.0') 에 걸려 400 이 된다")
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("branchName·maxCandidates·clientId 를 그대로 전달한다")
        void 전달값() {
            face가_돌려준다();

            useCase.execute(입력("85.00", 37));

            IdentifyCandidatesFaceFeignRequestDTO sent = face로_보낸_요청();
            assertThat(sent.getBranchName())
                    .as("branchName 은 프로젝트 생성 시 발급된 UUID 다 — API 키와 다른 값이다")
                    .isEqualTo(BRANCH);
            assertThat(sent.getDescriptor()).isEqualTo(DESCRIPTOR);
            assertThat(sent.getMaxCandidates()).isEqualTo(37);
            assertThat(sent.getTransactionUuid()).isEqualTo(TX);
            assertThat(sent.getClientId())
                    .as("X-Account-Id 가 없어도 null.toString() 이 되지 않도록 프로젝트 소유자를 쓴다")
                    .isEqualTo(ACCOUNT_ID.toString());
        }
    }

    @Nested
    @DisplayName("후보 없음")
    class 후보없음 {

        @Test
        @DisplayName("face 가 빈 목록이면 NOT_MATCH 로 남기고 빈 결과를 반환한다")
        void 임계치_통과자_없음() {
            face가_돌려준다();

            IdentifyCandidatesByDescriptorResult result = useCase.execute(입력("85.00", 10));

            assertThat(result.success()).isFalse();
            assertThat(result.candidates()).isEmpty();
            assertThat(result.failureType()).isEqualTo(ErrorType.NOT_MATCH.name());
            assertThat(result.threshold()).isEqualByComparingTo("85.00");

            assertThat(저장된_이력().getFailureType()).isEqualTo(ErrorType.NOT_MATCH.name());
            // 후보가 없으면 gate 저장소를 뒤질 이유가 없다.
            verify(biometricFeatureRepository, never())
                    .findAllByFeatureIdInAndProjectIdAndTypeAndIsDeletedFalse(any(), any(), any());
        }

        @Test
        @DisplayName("아깝게 미달한 경우 이력에 그 유사도가 남는다 — 0 으로 눕히지 않는다")
        void 근접_유사도_보존() {
            // 반박 리뷰 지적. 기존 1:N 은 fail(data.getSimilarity(), NOT_MATCH) 로 근접값을
            // 남긴다. 여기서 0 을 남기면 이 엔드포인트의 모든 근접 실패가 대시보드에서 0% 로
            // 보이고, "아무도 근접하지 않았다" 와 구분되지 않는다.
            face가_통과자없이_근접만_알려준다("0.84900");

            IdentifyCandidatesByDescriptorResult result = useCase.execute(입력("85.00", 10));

            assertThat(result.success()).isFalse();
            assertThat(저장된_이력().getSimilarity())
                    .as("84.90 이 남아야 '85 에 0.1 모자랐다' 를 이력만 보고 알 수 있다")
                    .isEqualByComparingTo("84.90");
        }

        @Test
        @DisplayName("근접자 자체가 없으면 유사도는 null 이다 — 0.00 은 '0% 였다' 는 거짓말이다")
        void 근접자_없음() {
            face가_통과자없이_근접만_알려준다(null);

            useCase.execute(입력("85.00", 10));

            assertThat(저장된_이력().getSimilarity()).isNull();
        }

        @Test
        @DisplayName("face 응답이 null 이어도 깨지지 않는다")
        void 응답_null() {
            given(faceService.identifyCandidatesByDescriptor(any())).willReturn(null);

            IdentifyCandidatesByDescriptorResult result = useCase.execute(입력("85.00", 10));

            assertThat(result.success()).isFalse();
            assertThat(result.candidates()).isEmpty();
        }
    }

    @Nested
    @DisplayName("gate 에 없는 후보")
    class 누락 {

        @Test
        @DisplayName("일부가 없으면 그 후보만 빼고 진행하고 WARN 을 남긴다")
        void 일부_누락() {
            face가_돌려준다("face-a", "0.97000", "ghost", "0.90000", "face-b", "0.88000");
            gate에_있다("face-a", "홍길동", "face-b", "김철수");

            IdentifyCandidatesByDescriptorResult result = useCase.execute(입력("85.00", 10));

            assertThat(result.success()).isTrue();
            assertThat(result.candidates())
                    .as("한 명이 없다고 목록 전체를 실패시키면 이 API 의 용도가 사라진다")
                    .extracting(IdentifyCandidatesByDescriptorResult.Candidate::featureId)
                    .containsExactly("face-a", "face-b");

            assertThat(경고들())
                    .as("조용히 건너뛰면 두 저장소가 어긋난 사실이 영영 드러나지 않는다")
                    .hasSize(1);
            assertThat(경고들().getFirst())
                    .contains("ghost")
                    .doesNotContain("face-a", "face-b");
        }

        @Test
        @DisplayName("최상위가 gate 에 없으면 이력의 유사도도 2등 것이라야 한다")
        void 대표값_유사도가_featureId와_같은_사람() {
            // 반박 리뷰 지적. featureId 는 '살아 있는 최상위' 로 고르면서 유사도만 '전체
            // 최상위' 에서 가져오면, 한 행 안에 A 의 featureId 와 B 의 유사도가 섞인다.
            face가_돌려준다("ghost", "0.99000", "face-b", "0.88000");
            gate에_있다("face-b", "김철수");

            IdentifyCandidatesByDescriptorResult result = useCase.execute(입력("85.00", 10));

            assertThat(result.candidates())
                    .extracting(IdentifyCandidatesByDescriptorResult.Candidate::featureId)
                    .containsExactly("face-b");

            MatchHistory saved = 저장된_이력();
            assertThat(saved.getFeatureId()).isEqualTo("face-b");
            assertThat(saved.getSimilarity())
                    .as("99.00 이 남으면 face-b 가 99% 로 매칭된 것처럼 보인다 — 실제로는 88% 다")
                    .isEqualByComparingTo("88.00");
        }

        @Test
        @DisplayName("전부 없으면 NOT_MATCH 가 아니라 INVALID_USER 로 남긴다")
        void 전부_누락() {
            face가_돌려준다("ghost-1", "0.97000", "ghost-2", "0.90000");
            given(biometricFeatureRepository.findAllByFeatureIdInAndProjectIdAndTypeAndIsDeletedFalse(
                    any(), any(), any())).willReturn(List.of());

            IdentifyCandidatesByDescriptorResult result = useCase.execute(입력("85.00", 10));

            assertThat(result.success()).isFalse();
            assertThat(result.candidates()).isEmpty();
            assertThat(result.failureType())
                    .as("매칭은 성공했는데 gate 데이터가 없는 것이다. NOT_MATCH 로 남기면 "
                            + "'유사한 사람이 없었다' 와 구분되지 않아 이력으로 원인을 못 찾는다")
                    .isEqualTo(ErrorType.INVALID_USER.name());

            assertThat(저장된_이력().getFailureType()).isEqualTo(ErrorType.INVALID_USER.name());
            assertThat(경고들()).hasSize(1);
        }

        @Test
        @DisplayName("삭제된 특징점은 조회 단계에서 걸러진다 — isDeleted=false 조건을 쓴다")
        void 삭제된_것은_조회하지_않는다() {
            face가_돌려준다("face-a", "0.97000");
            gate에_있다("face-a", "홍길동");

            useCase.execute(입력("85.00", 10));

            // 메서드 이름 자체가 조건을 담는다. isDeleted 조건이 빠진 메서드로 바꾸면
            // 삭제된 사용자가 후보로 되살아난다.
            verify(biometricFeatureRepository)
                    .findAllByFeatureIdInAndProjectIdAndTypeAndIsDeletedFalse(
                            List.of("face-a"), PROJECT_ID, FeatureType.FACE);
        }
    }

    @Nested
    @DisplayName("하위 서비스 실패")
    class 실패 {

        @Test
        @DisplayName("Feign 실패 — 사유를 이력에 남긴 뒤 예외를 그대로 전파한다")
        void feign_실패() {
            given(faceService.identifyCandidatesByDescriptor(any())).willThrow(
                    new CustomFeignException(ErrorType.FACE_NOT_FOUND.getCode(),
                            ErrorType.FACE_NOT_FOUND.name(), "no face"));

            assertThatThrownBy(() -> useCase.execute(입력("85.00", 10)))
                    .isInstanceOf(CustomFeignException.class);

            MatchHistory saved = 저장된_이력();
            assertThat(saved.getFailureType())
                    .as("failure_type 이 비면 이력 목록에서 '왜 실패했는지 알 수 없는 행' 이 된다")
                    .isEqualTo(ErrorType.FACE_NOT_FOUND.name());
            assertThat(saved.getSuccess()).isFalse();
        }
    }
}

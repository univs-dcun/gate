package ai.univs.gate.modules.feature.application.usecase.palm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.palm.PalmIdentifyInput;
import ai.univs.gate.modules.feature.application.result.palm.PalmIdentifyResult;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.feature.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.IdentifyPalmFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.IdentifyPalmFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.palm.PalmFeatureService;
import ai.univs.gate.support.feature.palm.PalmService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdentifyPalmUseCase 단위 테스트")
class IdentifyPalmUseCaseTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long PROJECT_ACCOUNT_ID = 10L;
    // 호출자 accountId — clientId에 프로젝트 소유자가 아닌 입력값이 쓰이는지 구분하기 위해 다른 값 사용
    private static final Long CALLER_ACCOUNT_ID = 99L;
    private static final Long SAVED_MATCH_HISTORY_ID = 100L;
    private static final String API_KEY = "gate_test-api-key";
    private static final String TRANSACTION_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String FILE_SERVER_PATH = "http://gateway/api/v1/files?filePath=";
    private static final String UPLOADED_IMAGE_PATH = "match/uploaded-palm.jpg";

    @Mock private MatchHistoryRepository matchHistoryRepository;
    @Mock private ProjectSettingsService projectSettingsService;
    @Mock private PalmFeatureService palmFeatureService;
    @Mock private ApiKeyService apiKeyService;
    @Mock private FileService fileService;
    @Mock private PalmService palmService;
    @Mock private BiometricFeatureRepository biometricFeatureRepository;

    @InjectMocks private IdentifyPalmUseCase identifyPalmUseCase;

    private Project project;
    private ApiKey apiKey;
    private ProjectSettings settings;
    private MockMultipartFile matchingImage;
    private PalmIdentifyInput input;

    @BeforeEach
    void setUp() {
        project = Project.builder()
                .id(PROJECT_ID)
                .accountId(PROJECT_ACCOUNT_ID)
                .projectName("gate-project")
                .branchName("branch-1")
                .status(ProjectStatus.ACTIVE)
                .build();
        apiKey = ApiKey.builder()
                .id(5L)
                .project(project)
                .apiKey(API_KEY)
                .secretKey("secret")
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC))
                .isActive(true)
                .build();
        matchingImage = new MockMultipartFile(
                "featureImage", "palm.jpg", "image/jpeg", "palm-bytes".getBytes());
        input = new PalmIdentifyInput(
                CallerType.API, CALLER_ACCOUNT_ID, API_KEY, matchingImage, TRANSACTION_UUID);
    }

    private void givenCommonFlow(boolean consentEnabled, String uploadedImagePath) {
        settings = ProjectSettings.builder()
                .id(2L)
                .project(project)
                .consentEnabled(consentEnabled)
                .build();
        given(apiKeyService.findByApiKey(API_KEY)).willReturn(apiKey);
        given(projectSettingsService.findByProject(project)).willReturn(settings);
        given(biometricFeatureRepository.countByProjectIdAndTypeAndIsDeletedFalse(PROJECT_ID, FeatureType.PALM))
                .willReturn(1L);
        given(fileService.uploadIfConsent(matchingImage, consentEnabled)).willReturn(uploadedImagePath);
        given(projectSettingsService.isLivenessEnabled(settings, FeatureType.PALM, LivenessOperation.IDENTIFY))
                .willReturn(true);
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);
        given(matchHistoryRepository.save(any(MatchHistory.class))).willAnswer(invocation -> {
            MatchHistory saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", SAVED_MATCH_HISTORY_ID);
            return saved;
        });
    }

    private BiometricFeature registeredFeature() {
        return BiometricFeature.builder()
                .id(7L)
                .project(project)
                .type(FeatureType.PALM)
                .featureId("registered-palm-id")
                .featureImagePath("feature/registered-palm.jpg")
                .description("홍길동")
                .isDeleted(false)
                .build();
    }

    private MatchHistory capturedMatchHistory() {
        ArgumentCaptor<MatchHistory> captor = ArgumentCaptor.forClass(MatchHistory.class);
        verify(matchHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("식별 성공 시 매칭 이력이 success 상태로 갱신되고 threshold를 포함한 successResult가 반환된다")
    void execute_success() {
        // given: palm 서비스는 similarity를 퍼센트(0~100) 문자열로 반환한다
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        given(palmService.identify(any(IdentifyPalmFeignRequestDTO.class)))
                .willReturn(IdentifyPalmFeignResponseDTO.builder()
                        .transactionUuid(TRANSACTION_UUID)
                        .palmId("registered-palm-id")
                        .similarity("87.5")
                        .threshold("80")
                        .result(true)
                        .build());
        given(palmFeatureService.getPalmFeatureByPalmIdAndProjectId("registered-palm-id", PROJECT_ID))
                .willReturn(registeredFeature());

        // when
        PalmIdentifyResult result = identifyPalmUseCase.execute(input);

        // then: 결과 필드 검증 (퍼센트 문자열 "87.5" → ÷100 → toPercent → 87.50)
        assertThat(result.matchingHistoryId()).isEqualTo(SAVED_MATCH_HISTORY_ID);
        assertThat(result.projectId()).isEqualTo(PROJECT_ID);
        assertThat(result.matchType()).isEqualTo(MatchType.IDENTIFY);
        assertThat(result.checkLiveness()).isTrue();
        assertThat(result.success()).isTrue();
        assertThat(result.palmFeatureId()).isEqualTo(7L);
        assertThat(result.featureId()).isEqualTo("registered-palm-id");
        assertThat(result.description()).isEqualTo("홍길동");
        assertThat(result.similarity()).isEqualTo(new BigDecimal("87.50"));
        assertThat(result.featureImagePath()).isEqualTo(FILE_SERVER_PATH + "feature/registered-palm.jpg");
        assertThat(result.matchingFeatureImagePath()).isEqualTo(FILE_SERVER_PATH + UPLOADED_IMAGE_PATH);
        assertThat(result.threshold()).isEqualTo("80");
        assertThat(result.failureType()).isNull();
        assertThat(result.transactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(result.consentSnapshot()).isTrue();

        // then: 저장된 매칭 이력의 상태 전이 검증
        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isTrue();
        assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("87.50"));
        assertThat(saved.getFeatureId()).isEqualTo("registered-palm-id");
        assertThat(saved.getUserDescription()).isEqualTo("홍길동");
        assertThat(saved.getFeatureSeq()).isEqualTo(7L);
        assertThat(saved.getMatchType()).isEqualTo(MatchType.IDENTIFY);
        assertThat(saved.getFeatureType()).isEqualTo(FeatureType.PALM);
        assertThat(saved.getCheckLiveness()).isTrue();
        assertThat(saved.getConsentSnapshot()).isTrue();
        assertThat(saved.getMatchedFeatureImagePath()).isEqualTo(UPLOADED_IMAGE_PATH);
        assertThat(saved.getTransactionUuid()).isEqualTo(TRANSACTION_UUID);

        // then: feign 요청 파라미터 검증 (clientId는 호출자 accountId)
        ArgumentCaptor<IdentifyPalmFeignRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(IdentifyPalmFeignRequestDTO.class);
        verify(palmService).identify(requestCaptor.capture());
        IdentifyPalmFeignRequestDTO request = requestCaptor.getValue();
        assertThat(request.getBranchName()).isEqualTo("branch-1");
        assertThat(request.getPalmImage()).isEqualTo(matchingImage);
        assertThat(request.getTransactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(request.getClientId()).isEqualTo(CALLER_ACCOUNT_ID.toString());
        assertThat(request.getCheckLiveness()).isTrue();
    }

    @Test
    @DisplayName("등록된 팜 사용자가 없으면 매칭 서버 호출 없이 NO_REGISTERED_PALM_USERS failResult를 반환한다")
    void execute_noRegisteredPalmUsers_returnsPreCheckFailResult() {
        // given
        settings = ProjectSettings.builder()
                .id(2L)
                .project(project)
                .consentEnabled(true)
                .build();
        given(apiKeyService.findByApiKey(API_KEY)).willReturn(apiKey);
        given(projectSettingsService.findByProject(project)).willReturn(settings);
        given(biometricFeatureRepository.countByProjectIdAndTypeAndIsDeletedFalse(PROJECT_ID, FeatureType.PALM))
                .willReturn(0L);
        given(fileService.uploadIfConsent(matchingImage, true)).willReturn(UPLOADED_IMAGE_PATH);
        given(projectSettingsService.isLivenessEnabled(settings, FeatureType.PALM, LivenessOperation.IDENTIFY))
                .willReturn(true);
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);
        given(matchHistoryRepository.save(any(MatchHistory.class))).willAnswer(invocation -> {
            MatchHistory saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", SAVED_MATCH_HISTORY_ID);
            return saved;
        });

        // when
        PalmIdentifyResult result = identifyPalmUseCase.execute(input);

        // then: 사전 차단 failResult 검증
        assertThat(result.success()).isFalse();
        assertThat(result.failureType()).isEqualTo("NO_REGISTERED_PALM_USERS");
        assertThat(result.similarity()).isEqualTo(new BigDecimal("0.00"));
        assertThat(result.matchingHistoryId()).isEqualTo(SAVED_MATCH_HISTORY_ID);
        assertThat(result.matchingFeatureImagePath()).isEqualTo(FILE_SERVER_PATH + UPLOADED_IMAGE_PATH);
        assertThat(result.transactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(result.consentSnapshot()).isTrue();

        // then: 사전 차단 이력도 저장되어야 한다
        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getFailureType()).isEqualTo("NO_REGISTERED_PALM_USERS");
        assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("0.00"));
        assertThat(saved.getMatchType()).isEqualTo(MatchType.IDENTIFY);
        assertThat(saved.getFeatureType()).isEqualTo(FeatureType.PALM);
        assertThat(saved.getCheckLiveness()).isTrue();

        // then: 매칭 서버와 특징 조회는 호출되지 않아야 한다
        verifyNoInteractions(palmService);
        verifyNoInteractions(palmFeatureService);
    }

    @Test
    @DisplayName("라이브니스 실패 타입의 CustomFeignException 발생 시 예외를 전파하지 않고 failResult를 반환한다")
    void execute_livenessFailure_returnsFailResult() {
        // given
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        given(palmService.identify(any(IdentifyPalmFeignRequestDTO.class)))
                .willThrow(new CustomFeignException("ML-101", "FAKE", "liveness failed"));

        // when
        PalmIdentifyResult result = identifyPalmUseCase.execute(input);

        // then: failResult 필드 검증
        assertThat(result.success()).isFalse();
        assertThat(result.failureType()).isEqualTo("FAKE");
        assertThat(result.similarity()).isEqualTo(new BigDecimal("0.00"));
        assertThat(result.featureId()).isNull();
        assertThat(result.matchingHistoryId()).isEqualTo(SAVED_MATCH_HISTORY_ID);
        assertThat(result.transactionUuid()).isEqualTo(TRANSACTION_UUID);

        // then: 매칭 이력 fail 상태 검증
        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getFailureType()).isEqualTo("FAKE");
        assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("0.00"));

        verifyNoInteractions(palmFeatureService);
    }

    @Test
    @DisplayName("face와 달리 라이브니스 타입이 아닌 CustomFeignException도 전파하지 않고 failResult를 반환한다")
    void execute_nonLivenessFeignException_returnsFailResult() {
        // given: face는 비라이브니스 타입을 rethrow 하지만 palm은 모든 CustomFeignException을 failResult로 흡수한다
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        given(palmService.identify(any(IdentifyPalmFeignRequestDTO.class)))
                .willThrow(new CustomFeignException("ML-500", "INTERNAL_ERROR", "matching server error"));

        // when
        PalmIdentifyResult result = identifyPalmUseCase.execute(input);

        // then
        assertThat(result.success()).isFalse();
        assertThat(result.failureType()).isEqualTo("INTERNAL_ERROR");
        assertThat(result.similarity()).isEqualTo(new BigDecimal("0.00"));

        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getFailureType()).isEqualTo("INTERNAL_ERROR");
        verifyNoInteractions(palmFeatureService);
    }

    @Test
    @DisplayName("매칭 결과가 false이면 PALM_NOT_MATCH 실패 타입과 응답 유사도로 failResult를 반환한다")
    void execute_notMatch_returnsFailResult() {
        // given
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        given(palmService.identify(any(IdentifyPalmFeignRequestDTO.class)))
                .willReturn(IdentifyPalmFeignResponseDTO.builder()
                        .transactionUuid(TRANSACTION_UUID)
                        .similarity("42.5")
                        .result(false)
                        .build());

        // when
        PalmIdentifyResult result = identifyPalmUseCase.execute(input);

        // then: face의 NOT_MATCH와 달리 palm 고유의 PALM_NOT_MATCH 타입이어야 한다
        assertThat(result.success()).isFalse();
        assertThat(result.failureType()).isEqualTo("PALM_NOT_MATCH");
        assertThat(result.similarity()).isEqualTo(new BigDecimal("42.50"));

        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getFailureType()).isEqualTo("PALM_NOT_MATCH");
        assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("42.50"));

        verifyNoInteractions(palmFeatureService);
    }

    @Test
    @DisplayName("응답 유사도가 숫자로 파싱되지 않으면 0.00으로 기록된다")
    void execute_invalidSimilarity_recordsZero() {
        // given
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        given(palmService.identify(any(IdentifyPalmFeignRequestDTO.class)))
                .willReturn(IdentifyPalmFeignResponseDTO.builder()
                        .transactionUuid(TRANSACTION_UUID)
                        .similarity("not-a-number")
                        .result(false)
                        .build());

        // when
        PalmIdentifyResult result = identifyPalmUseCase.execute(input);

        // then
        assertThat(result.similarity()).isEqualTo(new BigDecimal("0.00"));
        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("palmId로 등록 특징 조회 실패 시 예외를 전파하지 않고 failResult를 반환한다")
    void execute_featureNotFound_returnsFailResult() {
        // given
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        given(palmService.identify(any(IdentifyPalmFeignRequestDTO.class)))
                .willReturn(IdentifyPalmFeignResponseDTO.builder()
                        .transactionUuid(TRANSACTION_UUID)
                        .palmId("unknown-palm-id")
                        .similarity("90")
                        .result(true)
                        .build());
        given(palmFeatureService.getPalmFeatureByPalmIdAndProjectId("unknown-palm-id", PROJECT_ID))
                .willThrow(new CustomGateException(ErrorType.INVALID_USER));

        // when
        PalmIdentifyResult result = identifyPalmUseCase.execute(input);

        // then
        assertThat(result.success()).isFalse();
        assertThat(result.failureType()).isEqualTo(ErrorType.INVALID_USER.name());
        assertThat(result.similarity()).isEqualTo(new BigDecimal("0.00"));

        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getFailureType()).isEqualTo("INVALID_USER");
        assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("동의(consent)가 비활성화되면 업로드에 false가 전달되고 이미지 경로가 비어 있는 결과를 반환한다")
    void execute_consentDisabled() {
        // given
        givenCommonFlow(false, null);
        given(palmService.identify(any(IdentifyPalmFeignRequestDTO.class)))
                .willReturn(IdentifyPalmFeignResponseDTO.builder()
                        .transactionUuid(TRANSACTION_UUID)
                        .palmId("registered-palm-id")
                        .similarity("87.5")
                        .threshold("80")
                        .result(true)
                        .build());
        given(palmFeatureService.getPalmFeatureByPalmIdAndProjectId("registered-palm-id", PROJECT_ID))
                .willReturn(registeredFeature());

        // when
        PalmIdentifyResult result = identifyPalmUseCase.execute(input);

        // then: consentEnabled=false가 그대로 전달되어야 한다
        verify(fileService).uploadIfConsent(matchingImage, false);

        // then: 동의가 없으면 이미지 경로가 노출되지 않아야 한다
        assertThat(result.success()).isTrue();
        assertThat(result.consentSnapshot()).isFalse();
        assertThat(result.featureImagePath()).isEmpty();
        assertThat(result.matchingFeatureImagePath()).isEmpty();

        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getConsentSnapshot()).isFalse();
        assertThat(saved.getMatchedFeatureImagePath()).isNull();
    }
}

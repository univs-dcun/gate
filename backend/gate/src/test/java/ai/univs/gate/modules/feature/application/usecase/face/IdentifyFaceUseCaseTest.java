package ai.univs.gate.modules.feature.application.usecase.face;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.face.IdentifyInput;
import ai.univs.gate.modules.feature.application.result.face.IdentifyResult;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.IdentifyFaceFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.MatchFaceFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.face.FaceFeatureService;
import ai.univs.gate.support.feature.face.FaceService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.notify.UseCaseNotifyService;
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
@DisplayName("IdentifyFaceUseCase 단위 테스트")
class IdentifyFaceUseCaseTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final Long SAVED_MATCH_HISTORY_ID = 100L;
    private static final String API_KEY = "gate_test-api-key";
    private static final String TRANSACTION_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String FILE_SERVER_PATH = "http://gateway/api/v1/files?filePath=";
    private static final String UPLOADED_IMAGE_PATH = "match/uploaded-face.jpg";

    @Mock private MatchHistoryRepository matchHistoryRepository;
    @Mock private ProjectSettingsService projectSettingsService;
    @Mock private FaceFeatureService faceFeatureService;
    @Mock private ApiKeyService apiKeyService;
    @Mock private FileService fileService;
    @Mock private FaceService faceService;
    @Mock private UseCaseNotifyService useCaseNotifyService;

    @InjectMocks private IdentifyFaceUseCase identifyFaceUseCase;

    private Project project;
    private ApiKey apiKey;
    private MockMultipartFile matchingImage;
    private IdentifyInput input;

    @BeforeEach
    void setUp() {
        project = Project.builder()
                .id(PROJECT_ID)
                .accountId(ACCOUNT_ID)
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
                "matchingFeatureImage", "face.jpg", "image/jpeg", "face-bytes".getBytes());
        input = new IdentifyInput(CallerType.API, ACCOUNT_ID, API_KEY, matchingImage, TRANSACTION_UUID);
    }

    private void givenCommonFlow(boolean consentEnabled, String uploadedImagePath) {
        ProjectSettings settings = ProjectSettings.builder()
                .id(2L)
                .project(project)
                .consentEnabled(consentEnabled)
                .build();
        given(apiKeyService.findByApiKey(API_KEY)).willReturn(apiKey);
        given(projectSettingsService.findByProject(project)).willReturn(settings);
        given(fileService.uploadIfConsent(matchingImage, consentEnabled)).willReturn(uploadedImagePath);
        given(projectSettingsService.isLivenessEnabled(settings, FeatureType.FACE, LivenessOperation.IDENTIFY))
                .willReturn(true);
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
                .type(FeatureType.FACE)
                .featureId("registered-face-id")
                .featureImagePath("feature/registered.jpg")
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
    @DisplayName("식별 성공 시 매칭 이력이 success 상태로 갱신되고 successResult가 반환되며 알림이 발송된다")
    void execute_success() {
        // given
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        given(faceService.identify(any(IdentifyFaceFeignRequestDTO.class)))
                .willReturn(MatchFaceFeignResponseDTO.builder()
                        .transactionUuid(TRANSACTION_UUID)
                        .faceId("registered-face-id")
                        .similarity(new BigDecimal("0.87"))
                        .result(true)
                        .build());
        given(faceFeatureService.getFaceFeatureByFaceIdAndProjectId("registered-face-id", PROJECT_ID))
                .willReturn(registeredFeature());
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);

        // when
        IdentifyResult result = identifyFaceUseCase.execute(input);

        // then: 결과 필드 검증
        assertThat(result.matchingHistoryId()).isEqualTo(SAVED_MATCH_HISTORY_ID);
        assertThat(result.projectId()).isEqualTo(PROJECT_ID);
        assertThat(result.matchType()).isEqualTo(MatchType.IDENTIFY);
        assertThat(result.success()).isTrue();
        assertThat(result.featureId()).isEqualTo("registered-face-id");
        assertThat(result.description()).isEqualTo("홍길동");
        assertThat(result.similarity()).isEqualTo(new BigDecimal("87.00"));
        assertThat(result.featureImagePath()).isEqualTo(FILE_SERVER_PATH + "feature/registered.jpg");
        assertThat(result.matchingFeatureImagePath()).isEqualTo(FILE_SERVER_PATH + UPLOADED_IMAGE_PATH);
        assertThat(result.failureType()).isEmpty();
        assertThat(result.transactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(result.consentSnapshot()).isTrue();

        // then: 저장된 매칭 이력의 상태 전이 검증
        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isTrue();
        assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("87.00"));
        assertThat(saved.getFeatureId()).isEqualTo("registered-face-id");
        assertThat(saved.getUserDescription()).isEqualTo("홍길동");
        assertThat(saved.getFeatureSeq()).isEqualTo(7L);
        assertThat(saved.getMatchType()).isEqualTo(MatchType.IDENTIFY);
        assertThat(saved.getFeatureType()).isEqualTo(FeatureType.FACE);
        assertThat(saved.getCheckLiveness()).isTrue();
        assertThat(saved.getConsentSnapshot()).isTrue();
        assertThat(saved.getMatchedFeatureImagePath()).isEqualTo(UPLOADED_IMAGE_PATH);
        assertThat(saved.getTransactionUuid()).isEqualTo(TRANSACTION_UUID);

        // then: 상호작용 검증
        verify(fileService).uploadIfConsent(matchingImage, true);
        verify(useCaseNotifyService)
                .notify(CallerType.API, MatchType.IDENTIFY.name(), PROJECT_ID, TRANSACTION_UUID, result);

        // then: feign 요청 파라미터 검증
        ArgumentCaptor<IdentifyFaceFeignRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(IdentifyFaceFeignRequestDTO.class);
        verify(faceService).identify(requestCaptor.capture());
        IdentifyFaceFeignRequestDTO request = requestCaptor.getValue();
        assertThat(request.getBranchName()).isEqualTo("branch-1");
        assertThat(request.getFaceImage()).isEqualTo(matchingImage);
        assertThat(request.getTransactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(request.getClientId()).isEqualTo(ACCOUNT_ID.toString());
        assertThat(request.isCheckLiveness()).isTrue();
        assertThat(request.isCheckMultiFace()).isTrue();
    }

    @Test
    @DisplayName("라이브니스 실패 타입의 CustomFeignException 발생 시 예외를 전파하지 않고 failResult를 반환한다")
    void execute_livenessFailure_returnsFailResult() {
        // given
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        given(faceService.identify(any(IdentifyFaceFeignRequestDTO.class)))
                .willThrow(new CustomFeignException("ML-101", "FAKE", "liveness failed"));
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);

        // when
        IdentifyResult result = identifyFaceUseCase.execute(input);

        // then: failResult 필드 검증
        assertThat(result.success()).isFalse();
        assertThat(result.failureType()).isEqualTo("FAKE");
        assertThat(result.similarity()).isEqualTo(new BigDecimal("0.00"));
        assertThat(result.featureId()).isEmpty();
        assertThat(result.matchingHistoryId()).isEqualTo(SAVED_MATCH_HISTORY_ID);
        assertThat(result.transactionUuid()).isEqualTo(TRANSACTION_UUID);

        // then: 매칭 이력 fail 상태 검증
        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getFailureType()).isEqualTo("FAKE");
        assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("0.00"));

        // then: 상호작용 검증
        verify(useCaseNotifyService)
                .notify(CallerType.API, MatchType.IDENTIFY.name(), PROJECT_ID, TRANSACTION_UUID, result);
        verifyNoInteractions(faceFeatureService);
    }

    @Test
    @DisplayName("라이브니스 타입이 아닌 CustomFeignException은 그대로 전파된다")
    void execute_nonLivenessFeignException_rethrown() {
        // given
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        CustomFeignException exception =
                new CustomFeignException("ML-500", "INTERNAL_ERROR", "matching server error");
        given(faceService.identify(any(IdentifyFaceFeignRequestDTO.class))).willThrow(exception);

        // when & then
        assertThatThrownBy(() -> identifyFaceUseCase.execute(input)).isSameAs(exception);

        // then: 실패 결과 통지가 발생하지 않아야 한다
        verifyNoInteractions(useCaseNotifyService);
        verifyNoInteractions(faceFeatureService);
    }

    @Test
    @DisplayName("매칭 결과가 false이면 NOT_MATCH 실패 타입으로 failResult를 반환한다")
    void execute_notMatch_returnsFailResult() {
        // given
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        given(faceService.identify(any(IdentifyFaceFeignRequestDTO.class)))
                .willReturn(MatchFaceFeignResponseDTO.builder()
                        .transactionUuid(TRANSACTION_UUID)
                        .similarity(new BigDecimal("0.42"))
                        .result(false)
                        .build());
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);

        // when
        IdentifyResult result = identifyFaceUseCase.execute(input);

        // then
        assertThat(result.success()).isFalse();
        assertThat(result.failureType()).isEqualTo(ErrorType.NOT_MATCH.name());
        assertThat(result.similarity()).isEqualTo(new BigDecimal("42.00"));

        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getFailureType()).isEqualTo("NOT_MATCH");
        assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("42.00"));

        verify(useCaseNotifyService)
                .notify(CallerType.API, MatchType.IDENTIFY.name(), PROJECT_ID, TRANSACTION_UUID, result);
        verifyNoInteractions(faceFeatureService);
    }

    @Test
    @DisplayName("faceId로 등록 특징 조회 실패 시 예외를 전파하지 않고 failResult를 반환한다")
    void execute_featureNotFound_returnsFailResult() {
        // given
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        given(faceService.identify(any(IdentifyFaceFeignRequestDTO.class)))
                .willReturn(MatchFaceFeignResponseDTO.builder()
                        .transactionUuid(TRANSACTION_UUID)
                        .faceId("unknown-face-id")
                        .similarity(new BigDecimal("0.90"))
                        .result(true)
                        .build());
        given(faceFeatureService.getFaceFeatureByFaceIdAndProjectId("unknown-face-id", PROJECT_ID))
                .willThrow(new CustomGateException(ErrorType.INVALID_USER));
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);

        // when
        IdentifyResult result = identifyFaceUseCase.execute(input);

        // then
        assertThat(result.success()).isFalse();
        assertThat(result.failureType()).isEqualTo(ErrorType.INVALID_USER.name());
        assertThat(result.similarity()).isEqualTo(new BigDecimal("0.00"));

        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getFailureType()).isEqualTo("INVALID_USER");
        assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("0.00"));

        verify(useCaseNotifyService)
                .notify(CallerType.API, MatchType.IDENTIFY.name(), PROJECT_ID, TRANSACTION_UUID, result);
    }

    @Test
    @DisplayName("동의(consent)가 비활성화되면 업로드에 false가 전달되고 이미지 경로가 비어 있는 결과를 반환한다")
    void execute_consentDisabled() {
        // given
        givenCommonFlow(false, null);
        given(faceService.identify(any(IdentifyFaceFeignRequestDTO.class)))
                .willReturn(MatchFaceFeignResponseDTO.builder()
                        .transactionUuid(TRANSACTION_UUID)
                        .faceId("registered-face-id")
                        .similarity(new BigDecimal("0.87"))
                        .result(true)
                        .build());
        given(faceFeatureService.getFaceFeatureByFaceIdAndProjectId("registered-face-id", PROJECT_ID))
                .willReturn(registeredFeature());
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);

        // when
        IdentifyResult result = identifyFaceUseCase.execute(input);

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

package ai.univs.gate.modules.feature.application.usecase.face;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.face.VerifyByFaceIdInput;
import ai.univs.gate.modules.feature.application.result.face.VerifyByFaceIdResult;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.MatchFaceFeignResponseDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.VerifyFaceByFaceIdFeignRequestDTO;
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
@DisplayName("FaceVerifyByFeatureIdUseCase 단위 테스트")
class FaceVerifyByFeatureIdUseCaseTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final Long SAVED_MATCH_HISTORY_ID = 200L;
    private static final String API_KEY = "gate_test-api-key";
    private static final String INPUT_FACE_ID = "input-face-id";
    private static final String REGISTERED_FEATURE_ID = "registered-feature-id";
    private static final String TRANSACTION_UUID = "550e8400-e29b-41d4-a716-446655440001";
    private static final String FILE_SERVER_PATH = "http://gateway/api/v1/files?filePath=";
    private static final String UPLOADED_IMAGE_PATH = "match/uploaded-face.jpg";

    @Mock private MatchHistoryRepository matchHistoryRepository;
    @Mock private FileService fileService;
    @Mock private ApiKeyService apiKeyService;
    @Mock private ProjectSettingsService projectSettingsService;
    @Mock private FaceService faceService;
    @Mock private FaceFeatureService faceFeatureService;
    @Mock private UseCaseNotifyService useCaseNotifyService;

    @InjectMocks private FaceVerifyByFeatureIdUseCase faceVerifyByFeatureIdUseCase;

    private Project project;
    private ApiKey apiKey;
    private MockMultipartFile matchingImage;
    private VerifyByFaceIdInput input;

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
        input = new VerifyByFaceIdInput(
                CallerType.API, ACCOUNT_ID, API_KEY, INPUT_FACE_ID, matchingImage, TRANSACTION_UUID);
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
        given(projectSettingsService.isLivenessEnabled(settings, FeatureType.FACE, LivenessOperation.VERIFY_ID))
                .willReturn(true);
        given(matchHistoryRepository.save(any(MatchHistory.class))).willAnswer(invocation -> {
            MatchHistory saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", SAVED_MATCH_HISTORY_ID);
            return saved;
        });
    }

    private void givenFeatureFound() {
        BiometricFeature feature = BiometricFeature.builder()
                .id(7L)
                .project(project)
                .type(FeatureType.FACE)
                .featureId(REGISTERED_FEATURE_ID)
                .featureImagePath("feature/registered.jpg")
                .description("홍길동")
                .isDeleted(false)
                .build();
        given(faceFeatureService.getFaceFeatureByFaceIdAndProjectId(INPUT_FACE_ID, PROJECT_ID))
                .willReturn(feature);
    }

    private MatchHistory capturedMatchHistory() {
        ArgumentCaptor<MatchHistory> captor = ArgumentCaptor.forClass(MatchHistory.class);
        verify(matchHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("1:1 인증 성공 시 매칭 이력이 successById 상태로 갱신되고 successResult가 반환된다")
    void execute_success() {
        // given
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        givenFeatureFound();
        given(faceService.verifyByFaceId(any(VerifyFaceByFaceIdFeignRequestDTO.class)))
                .willReturn(MatchFaceFeignResponseDTO.builder()
                        .transactionUuid(TRANSACTION_UUID)
                        .similarity(new BigDecimal("0.91"))
                        .result(true)
                        .build());
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);

        // when
        VerifyByFaceIdResult result = faceVerifyByFeatureIdUseCase.execute(input);

        // then: 결과 필드 검증
        assertThat(result.matchingHistoryId()).isEqualTo(SAVED_MATCH_HISTORY_ID);
        assertThat(result.projectId()).isEqualTo(PROJECT_ID);
        assertThat(result.matchType()).isEqualTo(MatchType.VERIFY_ID);
        assertThat(result.success()).isTrue();
        assertThat(result.featureId()).isEqualTo(REGISTERED_FEATURE_ID);
        assertThat(result.matchingFeatureId()).isEqualTo(REGISTERED_FEATURE_ID);
        assertThat(result.description()).isEqualTo("홍길동");
        assertThat(result.similarity()).isEqualTo(new BigDecimal("91.00"));
        assertThat(result.featureImagePath()).isEqualTo(FILE_SERVER_PATH + "feature/registered.jpg");
        assertThat(result.matchingFeatureImagePath()).isEqualTo(FILE_SERVER_PATH + UPLOADED_IMAGE_PATH);
        assertThat(result.failureType()).isEmpty();
        assertThat(result.transactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(result.consentSnapshot()).isTrue();

        // then: 매칭 이력 상태 전이 검증 (updateBiometricFeature + successById)
        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isTrue();
        assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("91.00"));
        assertThat(saved.getFeatureId()).isEqualTo(REGISTERED_FEATURE_ID);
        assertThat(saved.getUserDescription()).isEqualTo("홍길동");
        assertThat(saved.getFeatureSeq()).isEqualTo(7L);
        assertThat(saved.getMatchType()).isEqualTo(MatchType.VERIFY_ID);
        assertThat(saved.getFeatureType()).isEqualTo(FeatureType.FACE);
        assertThat(saved.getCheckLiveness()).isTrue();
        assertThat(saved.getConsentSnapshot()).isTrue();

        // then: feign 요청 파라미터 검증 (등록 특징의 featureId가 사용되어야 한다)
        ArgumentCaptor<VerifyFaceByFaceIdFeignRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(VerifyFaceByFaceIdFeignRequestDTO.class);
        verify(faceService).verifyByFaceId(requestCaptor.capture());
        VerifyFaceByFaceIdFeignRequestDTO request = requestCaptor.getValue();
        assertThat(request.getBranchName()).isEqualTo("branch-1");
        assertThat(request.getFaceId()).isEqualTo(REGISTERED_FEATURE_ID);
        assertThat(request.getFaceImage()).isEqualTo(matchingImage);
        assertThat(request.getTransactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(request.getClientId()).isEqualTo(ACCOUNT_ID.toString());
        assertThat(request.isCheckLiveness()).isTrue();
        assertThat(request.isCheckMultiFace()).isTrue();

        // then: 알림 발송 검증
        verify(useCaseNotifyService)
                .notify(CallerType.API, MatchType.VERIFY_ID.name(), PROJECT_ID, TRANSACTION_UUID, result);
    }

    @Test
    @DisplayName("특징 조회 실패 시 feign 호출 전에 종료되어 faceService는 호출되지 않고 failResult가 반환된다")
    void execute_featureNotFound_beforeFeignCall() {
        // given
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        given(faceFeatureService.getFaceFeatureByFaceIdAndProjectId(INPUT_FACE_ID, PROJECT_ID))
                .willThrow(new CustomGateException(ErrorType.INVALID_USER));
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);

        // when
        VerifyByFaceIdResult result = faceVerifyByFeatureIdUseCase.execute(input);

        // then: feign 호출이 발생하지 않아야 한다
        verifyNoInteractions(faceService);

        // then: failResult 필드 검증
        assertThat(result.success()).isFalse();
        assertThat(result.failureType()).isEqualTo(ErrorType.INVALID_USER.name());
        assertThat(result.similarity()).isEqualTo(new BigDecimal("0.00"));
        assertThat(result.matchingFeatureId()).isEqualTo(INPUT_FACE_ID);
        assertThat(result.featureId()).isEmpty();

        // then: 매칭 이력 fail 상태 검증
        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getFailureType()).isEqualTo("INVALID_USER");
        assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("0.00"));
        assertThat(saved.getFeatureId()).isEqualTo(INPUT_FACE_ID);

        verify(useCaseNotifyService)
                .notify(CallerType.API, MatchType.VERIFY_ID.name(), PROJECT_ID, TRANSACTION_UUID, result);
    }

    @Test
    @DisplayName("라이브니스 실패 타입의 CustomFeignException 발생 시 예외를 전파하지 않고 failResult를 반환한다")
    void execute_livenessFailure_returnsFailResult() {
        // given
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        givenFeatureFound();
        given(faceService.verifyByFaceId(any(VerifyFaceByFaceIdFeignRequestDTO.class)))
                .willThrow(new CustomFeignException("ML-102", "EYES_CLOSED", "liveness failed"));
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);

        // when
        VerifyByFaceIdResult result = faceVerifyByFeatureIdUseCase.execute(input);

        // then
        assertThat(result.success()).isFalse();
        assertThat(result.failureType()).isEqualTo("EYES_CLOSED");
        assertThat(result.similarity()).isEqualTo(new BigDecimal("0.00"));

        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getFailureType()).isEqualTo("EYES_CLOSED");
        assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("0.00"));

        verify(useCaseNotifyService)
                .notify(CallerType.API, MatchType.VERIFY_ID.name(), PROJECT_ID, TRANSACTION_UUID, result);
    }

    @Test
    @DisplayName("라이브니스 타입이 아닌 CustomFeignException은 그대로 전파된다")
    void execute_nonLivenessFeignException_rethrown() {
        // given
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        givenFeatureFound();
        CustomFeignException exception =
                new CustomFeignException("ML-500", "INTERNAL_ERROR", "matching server error");
        given(faceService.verifyByFaceId(any(VerifyFaceByFaceIdFeignRequestDTO.class))).willThrow(exception);

        // when & then
        assertThatThrownBy(() -> faceVerifyByFeatureIdUseCase.execute(input)).isSameAs(exception);

        // then: 실패 결과 통지가 발생하지 않아야 한다
        verifyNoInteractions(useCaseNotifyService);
    }

    @Test
    @DisplayName("매칭 결과가 false이면 MISMATCH 실패 타입으로 failResult를 반환한다")
    void execute_mismatch_returnsFailResult() {
        // given
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        givenFeatureFound();
        given(faceService.verifyByFaceId(any(VerifyFaceByFaceIdFeignRequestDTO.class)))
                .willReturn(MatchFaceFeignResponseDTO.builder()
                        .transactionUuid(TRANSACTION_UUID)
                        .similarity(new BigDecimal("0.33"))
                        .result(false)
                        .build());
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);

        // when
        VerifyByFaceIdResult result = faceVerifyByFeatureIdUseCase.execute(input);

        // then
        assertThat(result.success()).isFalse();
        assertThat(result.failureType()).isEqualTo(ErrorType.MISMATCH.name());
        assertThat(result.similarity()).isEqualTo(new BigDecimal("33.00"));
        assertThat(result.matchingFeatureId()).isEqualTo(REGISTERED_FEATURE_ID);

        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getFailureType()).isEqualTo("MISMATCH");
        assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("33.00"));

        verify(useCaseNotifyService)
                .notify(CallerType.API, MatchType.VERIFY_ID.name(), PROJECT_ID, TRANSACTION_UUID, result);
    }

    @Test
    @DisplayName("동의(consent)가 비활성화되면 업로드에 false가 전달되고 이미지 경로가 비어 있는 결과를 반환한다")
    void execute_consentDisabled() {
        // given
        givenCommonFlow(false, null);
        givenFeatureFound();
        given(faceService.verifyByFaceId(any(VerifyFaceByFaceIdFeignRequestDTO.class)))
                .willReturn(MatchFaceFeignResponseDTO.builder()
                        .transactionUuid(TRANSACTION_UUID)
                        .similarity(new BigDecimal("0.91"))
                        .result(true)
                        .build());
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);

        // when
        VerifyByFaceIdResult result = faceVerifyByFeatureIdUseCase.execute(input);

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

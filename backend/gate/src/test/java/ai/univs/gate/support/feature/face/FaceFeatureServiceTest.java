package ai.univs.gate.support.feature.face;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.feature.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.CreateFaceFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
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
@DisplayName("FaceFeatureService 단위 테스트")
class FaceFeatureServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final Long SAVED_MATCH_HISTORY_ID = 100L;
    private static final Long SAVED_FEATURE_ID = 7L;
    private static final String API_KEY = "gate_test-api-key";
    private static final String TRANSACTION_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String UPLOADED_IMAGE_PATH = "feature/uploaded-face.jpg";
    private static final String CREATED_FACE_ID = "new-face-id";

    @Mock private BiometricFeatureRepository biometricFeatureRepository;
    @Mock private MatchHistoryRepository matchHistoryRepository;
    @Mock private ApiKeyService apiKeyService;
    @Mock private FileService fileService;
    @Mock private FaceService faceService;
    @Mock private ProjectSettingsService projectSettingsService;

    @InjectMocks private FaceFeatureService faceFeatureService;

    private Project project;
    private ApiKey apiKey;
    private ProjectSettings settings;
    private MockMultipartFile featureImage;

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
        featureImage = new MockMultipartFile(
                "featureImage", "face.jpg", "image/jpeg", "face-bytes".getBytes());
    }

    private void givenCommonFlow(boolean consentEnabled, boolean livenessEnabled, String uploadedImagePath) {
        settings = ProjectSettings.builder()
                .id(2L)
                .project(project)
                .consentEnabled(consentEnabled)
                .build();
        given(apiKeyService.findByApiKey(API_KEY)).willReturn(apiKey);
        given(projectSettingsService.findByProject(project)).willReturn(settings);
        given(fileService.uploadIfConsent(featureImage, consentEnabled)).willReturn(uploadedImagePath);
        given(projectSettingsService.isLivenessEnabled(settings, FeatureType.FACE, LivenessOperation.REGISTER))
                .willReturn(livenessEnabled);
        given(matchHistoryRepository.save(any(MatchHistory.class))).willAnswer(invocation -> {
            MatchHistory saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", SAVED_MATCH_HISTORY_ID);
            return saved;
        });
    }

    private MatchHistory capturedMatchHistory() {
        ArgumentCaptor<MatchHistory> captor = ArgumentCaptor.forClass(MatchHistory.class);
        verify(matchHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("등록 성공 시 특징이 저장되고 매칭 이력이 REGISTER success 상태로 갱신된다")
    void createFaceFeature_success() {
        // given
        givenCommonFlow(true, true, UPLOADED_IMAGE_PATH);
        given(faceService.createFace(any(CreateFaceFeignRequestDTO.class))).willReturn(CREATED_FACE_ID);
        given(biometricFeatureRepository.save(any(BiometricFeature.class))).willAnswer(invocation -> {
            BiometricFeature saved = invocation.getArgument(0);
            saved.setId(SAVED_FEATURE_ID);
            return saved;
        });

        // when
        CreateFaceFeatureServiceResult result =
                faceFeatureService.createFaceFeature(ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID);

        // then: 저장된 특징 필드 검증
        ArgumentCaptor<BiometricFeature> featureCaptor = ArgumentCaptor.forClass(BiometricFeature.class);
        verify(biometricFeatureRepository).save(featureCaptor.capture());
        BiometricFeature savedFeature = featureCaptor.getValue();
        assertThat(savedFeature.getProject()).isSameAs(project);
        assertThat(savedFeature.getType()).isEqualTo(FeatureType.FACE);
        assertThat(savedFeature.getFeatureId()).isEqualTo(CREATED_FACE_ID);
        assertThat(savedFeature.getFeatureImagePath()).isEqualTo(UPLOADED_IMAGE_PATH);
        assertThat(savedFeature.getDescription()).isEqualTo("홍길동");
        assertThat(savedFeature.isDeleted()).isFalse();
        assertThat(savedFeature.getTransactionUuid()).isEqualTo(TRANSACTION_UUID);

        // then: 매칭 이력 상태 전이 검증
        MatchHistory savedHistory = capturedMatchHistory();
        assertThat(savedHistory.getSuccess()).isTrue();
        assertThat(savedHistory.getMatchType()).isEqualTo(MatchType.REGISTER);
        assertThat(savedHistory.getFeatureType()).isEqualTo(FeatureType.FACE);
        assertThat(savedHistory.getCheckLiveness()).isTrue();
        assertThat(savedHistory.getConsentSnapshot()).isTrue();
        assertThat(savedHistory.getSimilarity()).isEqualTo(new BigDecimal("0.00"));
        assertThat(savedHistory.getFeatureId()).isEqualTo(CREATED_FACE_ID);
        assertThat(savedHistory.getUserDescription()).isEqualTo("홍길동");
        assertThat(savedHistory.getFeatureSeq()).isEqualTo(SAVED_FEATURE_ID);
        assertThat(savedHistory.getMatchedFeatureImagePath()).isEqualTo(UPLOADED_IMAGE_PATH);
        assertThat(savedHistory.getTransactionUuid()).isEqualTo(TRANSACTION_UUID);

        // then: feign 요청 파라미터 검증
        ArgumentCaptor<CreateFaceFeignRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(CreateFaceFeignRequestDTO.class);
        verify(faceService).createFace(requestCaptor.capture());
        CreateFaceFeignRequestDTO request = requestCaptor.getValue();
        assertThat(request.getBranchName()).isEqualTo("branch-1");
        assertThat(request.getFaceImage()).isEqualTo(featureImage);
        assertThat(request.getTransactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(request.getClientId()).isEqualTo(ACCOUNT_ID.toString());
        assertThat(request.isCheckLiveness()).isTrue();
        assertThat(request.isCheckMultiFace()).isTrue();

        // then: 결과 검증
        assertThat(result.biometricFeature()).isSameAs(savedFeature);
        assertThat(result.livenessChecked()).isTrue();
    }

    @Test
    @DisplayName("얼굴 등록 feign 예외 발생 시 이력을 fail로 남기고 예외를 그대로 전파하며 특징은 저장하지 않는다")
    void createFaceFeature_feignException_rethrown() {
        // given
        givenCommonFlow(true, true, UPLOADED_IMAGE_PATH);
        CustomFeignException exception = new CustomFeignException("ML-101", "FAKE", "liveness failed");
        given(faceService.createFace(any(CreateFaceFeignRequestDTO.class))).willThrow(exception);

        // when & then
        assertThatThrownBy(() ->
                faceFeatureService.createFaceFeature(ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID))
                .isSameAs(exception);

        // then: 이력 fail 상태 검증
        MatchHistory savedHistory = capturedMatchHistory();
        assertThat(savedHistory.getSuccess()).isFalse();
        assertThat(savedHistory.getFailureType()).isEqualTo("FAKE");
        assertThat(savedHistory.getSimilarity()).isEqualTo(new BigDecimal("0.00"));

        // then: 특징은 저장되지 않아야 한다
        verify(biometricFeatureRepository, never()).save(any(BiometricFeature.class));
    }

    @Test
    @DisplayName("동의와 라이브니스가 꺼져 있으면 이미지 경로 없이 저장되고 checkLiveness=false로 요청/기록된다")
    void createFaceFeature_consentAndLivenessDisabled() {
        // given
        givenCommonFlow(false, false, null);
        given(faceService.createFace(any(CreateFaceFeignRequestDTO.class))).willReturn(CREATED_FACE_ID);
        given(biometricFeatureRepository.save(any(BiometricFeature.class))).willAnswer(invocation -> {
            BiometricFeature saved = invocation.getArgument(0);
            saved.setId(SAVED_FEATURE_ID);
            return saved;
        });

        // when
        CreateFaceFeatureServiceResult result =
                faceFeatureService.createFaceFeature(ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID);

        // then
        verify(fileService).uploadIfConsent(featureImage, false);

        MatchHistory savedHistory = capturedMatchHistory();
        assertThat(savedHistory.getCheckLiveness()).isFalse();
        assertThat(savedHistory.getConsentSnapshot()).isFalse();
        assertThat(savedHistory.getMatchedFeatureImagePath()).isNull();

        ArgumentCaptor<CreateFaceFeignRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(CreateFaceFeignRequestDTO.class);
        verify(faceService).createFace(requestCaptor.capture());
        assertThat(requestCaptor.getValue().isCheckLiveness()).isFalse();
        assertThat(requestCaptor.getValue().isCheckMultiFace()).isFalse();

        assertThat(result.biometricFeature().getFeatureImagePath()).isNull();
        assertThat(result.livenessChecked()).isFalse();
    }

    @Test
    @DisplayName("faceId와 projectId로 삭제되지 않은 얼굴 특징을 조회한다")
    void getFaceFeatureByFaceIdAndProjectId_found() {
        // given
        BiometricFeature feature = BiometricFeature.builder()
                .id(SAVED_FEATURE_ID)
                .project(project)
                .type(FeatureType.FACE)
                .featureId(CREATED_FACE_ID)
                .isDeleted(false)
                .build();
        given(biometricFeatureRepository.findByFeatureIdAndProjectIdAndTypeAndIsDeletedFalse(
                        CREATED_FACE_ID, PROJECT_ID, FeatureType.FACE))
                .willReturn(Optional.of(feature));

        // when & then
        assertThat(faceFeatureService.getFaceFeatureByFaceIdAndProjectId(CREATED_FACE_ID, PROJECT_ID))
                .isSameAs(feature);
    }

    @Test
    @DisplayName("조회 결과가 없으면 INVALID_USER 예외가 발생한다")
    void getFaceFeatureByFaceIdAndProjectId_notFound_throwsInvalidUser() {
        // given
        given(biometricFeatureRepository.findByFeatureIdAndProjectIdAndTypeAndIsDeletedFalse(
                        "unknown-face-id", PROJECT_ID, FeatureType.FACE))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                faceFeatureService.getFaceFeatureByFaceIdAndProjectId("unknown-face-id", PROJECT_ID))
                .isInstanceOf(CustomGateException.class)
                .satisfies(e -> assertThat(((CustomGateException) e).getErrorType())
                        .isEqualTo(ErrorType.INVALID_USER));
    }
}

package ai.univs.gate.support.feature.face;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureActionType;
import ai.univs.gate.modules.feature.domain.repository.FeatureHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.CreateFaceFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("FaceFeatureService 단위 테스트")
class FaceFeatureServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final Long SAVED_FEATURE_ID = 7L;
    private static final String API_KEY = "gate_test-api-key";
    private static final String TRANSACTION_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String UPLOADED_IMAGE_PATH = "feature/uploaded-face.jpg";
    private static final String CREATED_FACE_ID = "new-face-id";

    @Mock private BiometricFeatureRepository biometricFeatureRepository;
    @Mock private FeatureHistoryRepository featureHistoryRepository;
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
        given(apiKeyService.findByApiKey(CallerType.API, API_KEY, ACCOUNT_ID)).willReturn(apiKey);
        given(projectSettingsService.findByProject(project)).willReturn(settings);
        given(fileService.uploadIfConsent(featureImage, consentEnabled)).willReturn(uploadedImagePath);
        given(projectSettingsService.isLivenessEnabled(settings, FeatureType.FACE, LivenessOperation.REGISTER))
                .willReturn(livenessEnabled);
        given(featureHistoryRepository.save(any(FeatureHistory.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    private FeatureHistory capturedFeatureHistory() {
        ArgumentCaptor<FeatureHistory> captor = ArgumentCaptor.forClass(FeatureHistory.class);
        verify(featureHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("UG-333: externalKey 는 앞뒤 공백을 지워 저장하고, 빈 값은 null 로 정규화한다 — 특징점 이력 스냅샷에도 같은 값이 실린다")
    void createFaceFeature_externalKey_정규화() {
        givenCommonFlow(true, true, UPLOADED_IMAGE_PATH);
        given(faceService.createFace(any(CreateFaceFeignRequestDTO.class))).willReturn(CREATED_FACE_ID);
        given(biometricFeatureRepository.save(any(BiometricFeature.class))).willAnswer(invocation -> invocation.getArgument(0));

        faceFeatureService.createFaceFeature(CallerType.API, ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID, "  cust-42 ");

        ArgumentCaptor<BiometricFeature> featureCaptor = ArgumentCaptor.forClass(BiometricFeature.class);
        verify(biometricFeatureRepository).save(featureCaptor.capture());
        assertThat(featureCaptor.getValue().getExternalKey()).isEqualTo("cust-42");
        assertThat(capturedFeatureHistory().getExternalKey()).as("이력 스냅샷").isEqualTo("cust-42");

        assertThat(FaceFeatureService.normalizeExternalKey("   ")).isNull();
        assertThat(FaceFeatureService.normalizeExternalKey(null)).isNull();
        assertThat(FaceFeatureService.normalizeExternalKey("")).isNull();
    }

    @Test
    @DisplayName("등록 성공 시 특징이 저장되고 특징점 이력(feature_history)이 REGISTER success 상태로 갱신된다")
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
                faceFeatureService.createFaceFeature(CallerType.API, ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID, null);

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

        // then (UG-325/326): feature_history 에 REGISTER 가 성공 상태 + 스냅샷으로 남는다. match_history 에는 쓰지 않는다.
        FeatureHistory featureHistory = capturedFeatureHistory();
        assertThat(featureHistory.getActionType()).isEqualTo(FeatureActionType.REGISTER);
        assertThat(featureHistory.getFeatureType()).isEqualTo(FeatureType.FACE);
        assertThat(featureHistory.isSuccess()).isTrue();
        assertThat(featureHistory.isCheckLiveness()).isTrue();
        assertThat(featureHistory.getConsentSnapshot()).isTrue();
        assertThat(featureHistory.getFeatureSeq()).isEqualTo(SAVED_FEATURE_ID);
        assertThat(featureHistory.getFeatureId()).isEqualTo(CREATED_FACE_ID);
        assertThat(featureHistory.getUserDescription()).isEqualTo("홍길동");
        assertThat(featureHistory.getFeatureImagePath()).isEqualTo(UPLOADED_IMAGE_PATH);
        assertThat(featureHistory.getTransactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(featureHistory.getFailureType()).isNull();

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
                faceFeatureService.createFaceFeature(CallerType.API, ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID, null))
                .isSameAs(exception);

        // then (UG-325): 실패한 등록 시도도 feature_history 에 남는다 — 스냅샷은 비고 사유만 있다
        FeatureHistory featureHistory = capturedFeatureHistory();
        assertThat(featureHistory.isSuccess()).isFalse();
        assertThat(featureHistory.getFailureType()).isEqualTo("FAKE");
        assertThat(featureHistory.getFeatureSeq()).isNull();
        assertThat(featureHistory.getFeatureId()).isNull();

        // then: 특징은 저장되지 않아야 한다
        verify(biometricFeatureRepository, never()).save(any(BiometricFeature.class));
    }

    @Test
    @DisplayName("face 가 응답을 못 주면(RemoteCallException) 이력은 INTERNAL_SERVER_ERROR 로 남고 특징은 저장하지 않는다")
    void 하위_장애_이력보존() {
        // PIT 가 잡아낸 구멍: CustomFeignException 경로만 테스트돼 RemoteCallException 쪽 fail() 호출을 지워도 초록이었다.
        givenCommonFlow(true, true, UPLOADED_IMAGE_PATH);
        RemoteCallException exception = new RemoteCallException(RemoteCallException.NO_RESPONSE, "face.createFace", new RuntimeException("timeout"));
        given(faceService.createFace(any(CreateFaceFeignRequestDTO.class))).willThrow(exception);

        assertThatThrownBy(() -> faceFeatureService.createFaceFeature(CallerType.API, ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID, null)).isSameAs(exception);

        FeatureHistory featureHistory = capturedFeatureHistory();
        assertThat(featureHistory.isSuccess()).isFalse();
        assertThat(featureHistory.getFailureType()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR.name());
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
                faceFeatureService.createFaceFeature(CallerType.API, ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID, null);

        // then
        verify(fileService).uploadIfConsent(featureImage, false);

        FeatureHistory featureHistory = capturedFeatureHistory();
        assertThat(featureHistory.isCheckLiveness()).isFalse();
        assertThat(featureHistory.getConsentSnapshot()).isFalse();
        assertThat(featureHistory.getFeatureImagePath()).isNull();

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

    @Test
    @DisplayName("UG-281: 소유 검증 실패 시 아무것도 쓰지 않고 즉시 중단한다")
    void createFaceFeature_소유검증_실패시_쓰기_없음() {
        // given: 검증이 이 메서드 맨 앞에서 실패한다
        given(apiKeyService.findByApiKey(CallerType.API, API_KEY, ACCOUNT_ID))
                .willThrow(new CustomGateException(ErrorType.API_KEY_NOT_FOUND));

        // when
        assertThatThrownBy(() -> faceFeatureService.createFaceFeature(
                CallerType.API, ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID, null))
                .isInstanceOf(CustomGateException.class);

        // then: 순서가 이 테스트의 본문이다.
        //
        // 예전에는 CreateFaceFeatureUseCase 가 이 메서드를 먼저 부르고 그 뒤에 소유를 확인했다.
        // 그런데 이 메서드는 REQUIRES_NEW 라 자기 트랜잭션을 따로 커밋한다. 즉 UseCase 가 나중에
        // 거부해도 남의 갤러리에는 이미 특징점과 REGISTER 이력이 남고, 이미지까지 업로드된 뒤였다.
        // 검증을 맨 앞으로 옮겨 그 창을 없앴다.
        verify(featureHistoryRepository, never()).save(any(FeatureHistory.class));
        verify(biometricFeatureRepository, never()).save(any(BiometricFeature.class));
        verify(fileService, never()).uploadIfConsent(any(), any(Boolean.class));
        verify(faceService, never()).createFace(any(CreateFaceFeignRequestDTO.class));
    }
}

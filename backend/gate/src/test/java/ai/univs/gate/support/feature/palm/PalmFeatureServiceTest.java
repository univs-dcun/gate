package ai.univs.gate.support.feature.palm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.support.history.HistoryRecorder;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureActionType;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.RegisterPalmFeignRequestDTO;
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
import ai.univs.gate.support.tx.RecordingTransactionTemplate;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("PalmFeatureService 단위 테스트")
class PalmFeatureServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final Long SAVED_FEATURE_ID = 7L;
    private static final String API_KEY = "gate_test-api-key";
    private static final String TRANSACTION_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String UPLOADED_IMAGE_PATH = "feature/uploaded-palm.jpg";
    private static final String CREATED_PALM_ID = "new-palm-id";

    @Mock private BiometricFeatureRepository biometricFeatureRepository;
    @Mock private HistoryRecorder historyRecorder;
    @Mock private ApiKeyService apiKeyService;
    @Mock private FileService fileService;
    @Mock private PalmService palmService;
    @Mock private ProjectSettingsService projectSettingsService;

    // UG-336: 성공 쓰기가 짧은 트랜잭션 안에서 일어난다. null 이면 NPE, 목이면 콜백이 안 돈다.
    @Spy private RecordingTransactionTemplate transactionTemplate = new RecordingTransactionTemplate();

    @InjectMocks private PalmFeatureService palmFeatureService;

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
                "featureImage", "palm.jpg", "image/jpeg", "palm-bytes".getBytes());
    }

    private void givenCommonFlow(boolean consentEnabled, boolean livenessEnabled, String uploadedImagePath) {
        givenCommonFlowWithoutStart(consentEnabled, livenessEnabled, uploadedImagePath);
        given(historyRecorder.start(any(FeatureHistory.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    /** 시작 이력 스텁을 테스트가 직접 거는 경우 (UG-336 — 호출 시점의 경계를 기록한다). strict stubs 라 겹쳐 걸 수 없다. */
    private void givenCommonFlowWithoutStart(boolean consentEnabled, boolean livenessEnabled, String uploadedImagePath) {
        settings = ProjectSettings.builder()
                .id(2L)
                .project(project)
                .consentEnabled(consentEnabled)
                .build();
        given(apiKeyService.findByApiKey(CallerType.API, API_KEY, ACCOUNT_ID)).willReturn(apiKey);
        given(projectSettingsService.findByProject(project)).willReturn(settings);
        given(fileService.uploadIfConsent(featureImage, consentEnabled)).willReturn(uploadedImagePath);
        given(projectSettingsService.isLivenessEnabled(settings, FeatureType.PALM, LivenessOperation.REGISTER))
                .willReturn(livenessEnabled);
    }

    private FeatureHistory capturedFeatureHistory() {
        ArgumentCaptor<FeatureHistory> captor = ArgumentCaptor.forClass(FeatureHistory.class);
        verify(historyRecorder).start(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("등록 성공 시 특징이 저장되고 특징점 이력(feature_history)이 REGISTER success 상태로 갱신된다")
    void createPalmFeature_success() {
        // given
        givenCommonFlow(true, true, UPLOADED_IMAGE_PATH);
        given(palmService.registerPalm(any(RegisterPalmFeignRequestDTO.class))).willReturn(CREATED_PALM_ID);
        given(biometricFeatureRepository.save(any(BiometricFeature.class))).willAnswer(invocation -> {
            BiometricFeature saved = invocation.getArgument(0);
            saved.setId(SAVED_FEATURE_ID);
            return saved;
        });

        // when
        CreatePalmFeatureServiceResult result =
                palmFeatureService.createPalmFeature(CallerType.API, ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID, "  cust-42 ");

        // then: 저장된 특징 필드 검증
        ArgumentCaptor<BiometricFeature> featureCaptor = ArgumentCaptor.forClass(BiometricFeature.class);
        verify(biometricFeatureRepository).save(featureCaptor.capture());
        BiometricFeature savedFeature = featureCaptor.getValue();
        assertThat(savedFeature.getProject()).isSameAs(project);
        assertThat(savedFeature.getType()).isEqualTo(FeatureType.PALM);
        assertThat(savedFeature.getFeatureId()).isEqualTo(CREATED_PALM_ID);
        // UG-333: 예전엔 DTO 가 받기만 하고 여기서 버렸다 — 저장·정규화(trim)를 못박는다
        assertThat(savedFeature.getExternalKey()).isEqualTo("cust-42");
        assertThat(savedFeature.getFeatureImagePath()).isEqualTo(UPLOADED_IMAGE_PATH);
        assertThat(savedFeature.getDescription()).isEqualTo("홍길동");
        assertThat(savedFeature.isDeleted()).isFalse();
        assertThat(savedFeature.getTransactionUuid()).isEqualTo(TRANSACTION_UUID);

        // then (UG-325/326): feature_history 에 REGISTER 가 성공 상태 + 스냅샷으로 남는다. match_history 에는 쓰지 않는다.
        FeatureHistory featureHistory = capturedFeatureHistory();
        assertThat(featureHistory.getActionType()).isEqualTo(FeatureActionType.REGISTER);
        assertThat(featureHistory.getFeatureType()).isEqualTo(FeatureType.PALM);
        assertThat(featureHistory.isSuccess()).isTrue();
        assertThat(featureHistory.isCheckLiveness()).isTrue();
        assertThat(featureHistory.getConsentSnapshot()).isTrue();
        assertThat(featureHistory.getFeatureSeq()).isEqualTo(SAVED_FEATURE_ID);
        assertThat(featureHistory.getFeatureId()).isEqualTo(CREATED_PALM_ID);
        assertThat(featureHistory.getUserDescription()).isEqualTo("홍길동");
        assertThat(featureHistory.getFeatureImagePath()).isEqualTo(UPLOADED_IMAGE_PATH);
        assertThat(featureHistory.getTransactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(featureHistory.getFailureType()).isNull();

        // then: feign 요청 파라미터 검증 (face와 달리 checkMultiFace 없음)
        ArgumentCaptor<RegisterPalmFeignRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(RegisterPalmFeignRequestDTO.class);
        verify(palmService).registerPalm(requestCaptor.capture());
        RegisterPalmFeignRequestDTO request = requestCaptor.getValue();
        assertThat(request.getBranchName()).isEqualTo("branch-1");
        assertThat(request.getPalmImage()).isEqualTo(featureImage);
        assertThat(request.getTransactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(request.getClientId()).isEqualTo(ACCOUNT_ID.toString());
        assertThat(request.getCheckLiveness()).isTrue();

        // then: 결과 검증
        assertThat(result.biometricFeature()).isSameAs(savedFeature);
        assertThat(result.livenessChecked()).isTrue();
    }

    @Test
    @DisplayName("팜 등록 feign 예외 발생 시 이력을 fail로 남기고 예외를 그대로 전파하며 특징은 저장하지 않는다")
    void createPalmFeature_feignException_rethrown() {
        // given
        givenCommonFlow(true, true, UPLOADED_IMAGE_PATH);
        CustomFeignException exception = new CustomFeignException("ML-101", "FAKE", "liveness failed");
        given(palmService.registerPalm(any(RegisterPalmFeignRequestDTO.class))).willThrow(exception);

        // when & then
        assertThatThrownBy(() ->
                palmFeatureService.createPalmFeature(CallerType.API, ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID, null))
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
    @DisplayName("palm 가 응답을 못 주면(RemoteCallException) 이력은 INTERNAL_SERVER_ERROR 로 남고 특징은 저장하지 않는다")
    void 하위_장애_이력보존() {
        // PIT 가 잡아낸 구멍: CustomFeignException 경로만 테스트돼 RemoteCallException 쪽 fail() 호출을 지워도 초록이었다.
        givenCommonFlow(true, true, UPLOADED_IMAGE_PATH);
        RemoteCallException exception = new RemoteCallException(RemoteCallException.NO_RESPONSE, "palm.registerPalm", new RuntimeException("timeout"));
        given(palmService.registerPalm(any(RegisterPalmFeignRequestDTO.class))).willThrow(exception);

        assertThatThrownBy(() -> palmFeatureService.createPalmFeature(CallerType.API, ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID, null)).isSameAs(exception);

        FeatureHistory featureHistory = capturedFeatureHistory();
        assertThat(featureHistory.isSuccess()).isFalse();
        assertThat(featureHistory.getFailureType()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR.name());
        verify(biometricFeatureRepository, never()).save(any(BiometricFeature.class));
    }

    @Test
    @DisplayName("동의와 라이브니스가 꺼져 있으면 이미지 경로 없이 저장되고 checkLiveness=false로 요청/기록된다")
    void createPalmFeature_consentAndLivenessDisabled() {
        // given
        givenCommonFlow(false, false, null);
        given(palmService.registerPalm(any(RegisterPalmFeignRequestDTO.class))).willReturn(CREATED_PALM_ID);
        given(biometricFeatureRepository.save(any(BiometricFeature.class))).willAnswer(invocation -> {
            BiometricFeature saved = invocation.getArgument(0);
            saved.setId(SAVED_FEATURE_ID);
            return saved;
        });

        // when
        CreatePalmFeatureServiceResult result =
                palmFeatureService.createPalmFeature(CallerType.API, ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID, null);

        // then
        verify(fileService).uploadIfConsent(featureImage, false);

        FeatureHistory featureHistory = capturedFeatureHistory();
        assertThat(featureHistory.isCheckLiveness()).isFalse();
        assertThat(featureHistory.getConsentSnapshot()).isFalse();
        assertThat(featureHistory.getFeatureImagePath()).isNull();

        ArgumentCaptor<RegisterPalmFeignRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(RegisterPalmFeignRequestDTO.class);
        verify(palmService).registerPalm(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getCheckLiveness()).isFalse();

        assertThat(result.biometricFeature().getFeatureImagePath()).isNull();
        assertThat(result.livenessChecked()).isFalse();
    }

    @Test
    @DisplayName("palmId와 projectId로 삭제되지 않은 팜 특징을 조회한다")
    void getPalmFeatureByPalmIdAndProjectId_found() {
        // given
        BiometricFeature feature = BiometricFeature.builder()
                .id(SAVED_FEATURE_ID)
                .project(project)
                .type(FeatureType.PALM)
                .featureId(CREATED_PALM_ID)
                .isDeleted(false)
                .build();
        given(biometricFeatureRepository.findByFeatureIdAndProjectIdAndTypeAndIsDeletedFalse(
                        CREATED_PALM_ID, PROJECT_ID, FeatureType.PALM))
                .willReturn(Optional.of(feature));

        // when & then
        assertThat(palmFeatureService.getPalmFeatureByPalmIdAndProjectId(CREATED_PALM_ID, PROJECT_ID))
                .isSameAs(feature);
    }

    @Test
    @DisplayName("조회 결과가 없으면 INVALID_USER 예외가 발생한다")
    void getPalmFeatureByPalmIdAndProjectId_notFound_throwsInvalidUser() {
        // given
        given(biometricFeatureRepository.findByFeatureIdAndProjectIdAndTypeAndIsDeletedFalse(
                        "unknown-palm-id", PROJECT_ID, FeatureType.PALM))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                palmFeatureService.getPalmFeatureByPalmIdAndProjectId("unknown-palm-id", PROJECT_ID))
                .isInstanceOf(CustomGateException.class)
                .satisfies(e -> assertThat(((CustomGateException) e).getErrorType())
                        .isEqualTo(ErrorType.INVALID_USER));
    }

    @Test
    @DisplayName("UG-281: 소유 검증 실패 시 아무것도 쓰지 않고 즉시 중단한다")
    void createPalmFeature_소유검증_실패시_쓰기_없음() {
        // given: 검증이 이 메서드 맨 앞에서 실패한다
        given(apiKeyService.findByApiKey(CallerType.API, API_KEY, ACCOUNT_ID))
                .willThrow(new CustomGateException(ErrorType.API_KEY_NOT_FOUND));

        // when
        assertThatThrownBy(() -> palmFeatureService.createPalmFeature(
                CallerType.API, ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID, null))
                .isInstanceOf(CustomGateException.class);

        // then: FaceFeatureService 와 짝을 이루는 테스트다. 얼굴 쪽에만 있으면 손바닥 등록에서
        // 검증이 뒤로 밀려도 아무도 모른다 — 이 메서드도 REQUIRES_NEW 라 같은 고아 위험을 갖는다.
        verify(historyRecorder, never()).start(any(FeatureHistory.class));
        verify(biometricFeatureRepository, never()).save(any(BiometricFeature.class));
        verify(fileService, never()).uploadIfConsent(any(), any(Boolean.class));
    }

    /**
     * <b>특징점 저장과 성공 이력만 트랜잭션 안에서 일어난다</b> (UG-336).
     *
     * <p>예전에는 메서드 전체가 트랜잭션이었다. 그러면 첫 조회에서 잡은 커넥션을 원격 호출 내내
     * 붙들고, 그 안에서 시작 이력({@code REQUIRES_NEW})이 두 번째 커넥션을 요구한다 — 동시
     * 등록이 풀 크기만큼 몰리면 서로를 기다리며 멈춘다.
     *
     * <p>반대로 저장과 성공 이력이 경계 밖으로 새면 따로 커밋돼 "특징점은 없는데 등록 성공
     * 이력만 있는" 상태가 가능해진다(UG-293 반박 리뷰). 호출 <b>시점</b>에 경계 안이었는지를
     * 기록해 두 방향을 함께 고정한다.
     */
    @Test
    @DisplayName("UG-336: 시작 이력·원격 호출은 트랜잭션 밖, 특징점 저장·성공 이력은 안이다")
    void 성공_쓰기만_트랜잭션_안이다() {
        givenCommonFlowWithoutStart(true, true, UPLOADED_IMAGE_PATH);
        java.util.List<String> 기록 = new java.util.ArrayList<>();
        given(historyRecorder.start(any(FeatureHistory.class)))
                .willAnswer(inv -> { 기록.add("start:" + transactionTemplate.isActive()); return inv.getArgument(0); });
        given(palmService.registerPalm(any(RegisterPalmFeignRequestDTO.class)))
                .willAnswer(inv -> { 기록.add("원격:" + transactionTemplate.isActive()); return CREATED_PALM_ID; });
        given(biometricFeatureRepository.save(any(BiometricFeature.class))).willAnswer(inv -> {
            기록.add("save:" + transactionTemplate.isActive());
            BiometricFeature saved = inv.getArgument(0);
            saved.setId(SAVED_FEATURE_ID);
            return saved;
        });
        willAnswer(inv -> { 기록.add("succeed:" + transactionTemplate.isActive()); return null; })
                .given(historyRecorder).succeed(any(FeatureHistory.class));

        palmFeatureService.createPalmFeature(CallerType.API, ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID, null);

        assertThat(기록).containsExactly(
                "start:false",    // REQUIRES_NEW 가 바깥 트랜잭션 없이 → 커넥션 하나
                "원격:false",     // 원격 호출 동안 커넥션을 쥐지 않는다
                "save:true",      // 특징점 저장과
                "succeed:true");  // 성공 이력이 한 트랜잭션
        assertThat(transactionTemplate.executions()).as("경계는 성공 블록 하나뿐이다").isEqualTo(1);
    }
}

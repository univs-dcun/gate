package ai.univs.gate.modules.feature.application.usecase.palm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.palm.PalmLivenessInput;
import ai.univs.gate.modules.feature.application.result.palm.PalmLivenessResult;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.LivenessPalmFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.LivenessPalmFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.support.api_key.ApiKeyService;
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
@DisplayName("LivenessPalmUseCase 단위 테스트")
class LivenessPalmUseCaseTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long PROJECT_ACCOUNT_ID = 10L;
    // 호출자 accountId — clientId에 프로젝트 소유자 accountId가 쓰이는 것을 구분하기 위해 다른 값 사용
    private static final Long CALLER_ACCOUNT_ID = 99L;
    private static final Long SAVED_MATCH_HISTORY_ID = 100L;
    private static final String API_KEY = "gate_test-api-key";
    private static final String TRANSACTION_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String UPLOADED_IMAGE_PATH = "match/uploaded-palm.jpg";

    @Mock private MatchHistoryRepository matchHistoryRepository;
    @Mock private ApiKeyService apiKeyService;
    @Mock private FileService fileService;
    @Mock private PalmService palmService;
    @Mock private ProjectSettingsService projectSettingsService;

    @InjectMocks private LivenessPalmUseCase livenessPalmUseCase;

    private Project project;
    private ApiKey apiKey;
    private MockMultipartFile featureImage;
    private PalmLivenessInput input;

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
        featureImage = new MockMultipartFile(
                "featureImage", "palm.jpg", "image/jpeg", "palm-bytes".getBytes());
        input = new PalmLivenessInput(
                CallerType.API, CALLER_ACCOUNT_ID, API_KEY, featureImage, TRANSACTION_UUID);
    }

    private void givenCommonFlow(boolean consentEnabled, String uploadedImagePath) {
        ProjectSettings settings = ProjectSettings.builder()
                .id(2L)
                .project(project)
                .consentEnabled(consentEnabled)
                .build();
        given(apiKeyService.findByApiKey(API_KEY)).willReturn(apiKey);
        given(projectSettingsService.findByProject(project)).willReturn(settings);
        given(fileService.uploadIfConsent(featureImage, consentEnabled)).willReturn(uploadedImagePath);
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
    @DisplayName("라이브니스 성공 시 이력이 사용자 정보 없이 success 상태로 갱신되고 응답 값이 그대로 반환된다")
    void execute_success() {
        // given: palm 서비스는 score를 퍼센트(0~100)로 반환한다
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        given(palmService.liveness(any(LivenessPalmFeignRequestDTO.class)))
                .willReturn(LivenessPalmFeignResponseDTO.builder()
                        .success(true)
                        .score(91.24)
                        .threshold(80.0)
                        .message("live")
                        .build());

        // when
        PalmLivenessResult result = livenessPalmUseCase.execute(input);

        // then: 결과는 feign 응답 값을 그대로 노출한다 (score는 변환하지 않는다)
        assertThat(result.success()).isTrue();
        assertThat(result.score()).isEqualTo(91.24);
        assertThat(result.threshold()).isEqualTo(80.0);
        assertThat(result.message()).isEqualTo("live");
        assertThat(result.transactionUuid()).isEqualTo(TRANSACTION_UUID);

        // then: 이력 상태 전이 — 1:1 성공은 사용자 정보를 남기지 않는다
        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isTrue();
        assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("91.24"));
        assertThat(saved.getFeatureId()).isNull();
        assertThat(saved.getUserDescription()).isEmpty();
        assertThat(saved.getMatchType()).isEqualTo(MatchType.LIVENESS);
        assertThat(saved.getFeatureType()).isEqualTo(FeatureType.PALM);
        assertThat(saved.getCheckLiveness()).isTrue();
        assertThat(saved.getConsentSnapshot()).isTrue();
        assertThat(saved.getMatchedFeatureImagePath()).isEqualTo(UPLOADED_IMAGE_PATH);
        assertThat(saved.getTransactionUuid()).isEqualTo(TRANSACTION_UUID);

        // then: feign 요청 파라미터 — clientId는 입력 accountId가 아닌 프로젝트 소유자 accountId다
        ArgumentCaptor<LivenessPalmFeignRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(LivenessPalmFeignRequestDTO.class);
        verify(palmService).liveness(requestCaptor.capture());
        LivenessPalmFeignRequestDTO request = requestCaptor.getValue();
        assertThat(request.getPalmImage()).isEqualTo(featureImage);
        assertThat(request.getTransactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(request.getClientId()).isEqualTo(PROJECT_ACCOUNT_ID.toString());
    }

    @Test
    @DisplayName("라이브니스 실패 시 응답 메시지가 대문자로 실패 타입에 기록되고 결과는 실패로 반환된다")
    void execute_failure_recordsUppercasedMessage() {
        // given
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        given(palmService.liveness(any(LivenessPalmFeignRequestDTO.class)))
                .willReturn(LivenessPalmFeignResponseDTO.builder()
                        .success(false)
                        .score(12.5)
                        .threshold(80.0)
                        .message("spoof")
                        .build());

        // when
        PalmLivenessResult result = livenessPalmUseCase.execute(input);

        // then: 결과는 원본 메시지, 이력은 대문자 실패 타입
        assertThat(result.success()).isFalse();
        assertThat(result.score()).isEqualTo(12.5);
        assertThat(result.message()).isEqualTo("spoof");

        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getFailureType()).isEqualTo("SPOOF");
        assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("12.50"));
    }

    @Test
    @DisplayName("라이브니스 실패 응답에 메시지가 없으면 실패 타입이 LIVENESS_FAILED로 기록된다")
    void execute_failureWithoutMessage_recordsDefaultFailureType() {
        // given
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        given(palmService.liveness(any(LivenessPalmFeignRequestDTO.class)))
                .willReturn(LivenessPalmFeignResponseDTO.builder()
                        .success(false)
                        .score(0.0)
                        .threshold(80.0)
                        .message(null)
                        .build());

        // when
        PalmLivenessResult result = livenessPalmUseCase.execute(input);

        // then
        assertThat(result.success()).isFalse();
        assertThat(result.message()).isNull();

        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getFailureType()).isEqualTo("LIVENESS_FAILED");
        assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("palm 서비스가 CustomFeignException을 던지면 이력은 저장된 채 예외가 그대로 전파된다")
    void execute_feignException_propagated() {
        // given: identify와 달리 liveness는 CustomFeignException을 흡수하지 않는다
        givenCommonFlow(true, UPLOADED_IMAGE_PATH);
        CustomFeignException exception = new CustomFeignException("ML-500", "INTERNAL_ERROR", "palm server error");
        given(palmService.liveness(any(LivenessPalmFeignRequestDTO.class))).willThrow(exception);

        // when & then
        assertThatThrownBy(() -> livenessPalmUseCase.execute(input)).isSameAs(exception);

        // then: 이력은 실패 상태 그대로 저장되어 있어야 한다 (fail 전이는 일어나지 않음)
        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getFailureType()).isNull();
    }

    @Test
    @DisplayName("동의(consent)가 비활성화되면 업로드에 false가 전달되고 이력에 이미지 경로가 남지 않는다")
    void execute_consentDisabled() {
        // given
        givenCommonFlow(false, null);
        given(palmService.liveness(any(LivenessPalmFeignRequestDTO.class)))
                .willReturn(LivenessPalmFeignResponseDTO.builder()
                        .success(true)
                        .score(91.24)
                        .threshold(80.0)
                        .message("live")
                        .build());

        // when
        livenessPalmUseCase.execute(input);

        // then
        verify(fileService).uploadIfConsent(featureImage, false);

        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getConsentSnapshot()).isFalse();
        assertThat(saved.getMatchedFeatureImagePath()).isNull();
    }
}

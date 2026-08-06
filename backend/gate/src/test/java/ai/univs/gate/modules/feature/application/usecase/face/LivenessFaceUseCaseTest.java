package ai.univs.gate.modules.feature.application.usecase.face;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.face.LivenessInput;
import ai.univs.gate.modules.feature.application.result.face.LivenessResult;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.LivenessFaceFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.LivenessFaceFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
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

/**
 * UG-280 3차 반박 리뷰에서 신설.
 *
 * <p>face 라이브니스는 단독으로 가장 많이 호출되는 경로인데 단위 테스트가 하나도 없었다. palm 쪽
 * ({@code LivenessPalmUseCaseTest})만 있어서, 두 경로의 방어 수준이 벌어진 것을 아무도 못 봤다 —
 * 실제로 {@code prdioctionDesc} null 가드가 palm 에만 있었다.
 *
 * <p>여기서 검증하는 것의 핵심은 <b>이력 행이 살아남는가</b>다. 이 UseCase 는 하위 서비스를 부르기
 * 전에 {@code MatchHistory} 를 저장하고, 그 트랜잭션은 {@code REQUIRES_NEW} 다.
 * {@code noRollbackFor} 목록에 없는 예외가 그 경계를 넘으면 이미 저장한 행이 사라진다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LivenessFaceUseCase 단위 테스트")
class LivenessFaceUseCaseTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long PROJECT_ACCOUNT_ID = 10L;
    private static final Long CALLER_ACCOUNT_ID = 99L;
    private static final Long SAVED_MATCH_HISTORY_ID = 100L;
    private static final String API_KEY = "gate_test-api-key";
    private static final String TRANSACTION_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String UPLOADED_IMAGE_PATH = "match/uploaded-face.jpg";

    @Mock private MatchHistoryRepository matchHistoryRepository;
    @Mock private ApiKeyService apiKeyService;
    @Mock private FileService fileService;
    @Mock private FaceService faceService;
    @Mock private ProjectSettingsService projectSettingsService;
    @Mock private UseCaseNotifyService useCaseNotifyService;

    @InjectMocks private LivenessFaceUseCase livenessFaceUseCase;

    private Project project;
    private ApiKey apiKey;
    private MockMultipartFile featureImage;
    private LivenessInput input;

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
                "featureImage", "face.jpg", "image/jpeg", "face-bytes".getBytes());
        input = new LivenessInput(
                CallerType.API, CALLER_ACCOUNT_ID, API_KEY, featureImage, TRANSACTION_UUID);
    }

    private void givenCommonFlow() {
        ProjectSettings settings = ProjectSettings.builder()
                .id(2L)
                .project(project)
                .consentEnabled(true)
                .build();
        given(apiKeyService.findByApiKey(CallerType.API, API_KEY, CALLER_ACCOUNT_ID)).willReturn(apiKey);
        given(projectSettingsService.findByProject(project)).willReturn(settings);
        given(fileService.uploadIfConsent(featureImage, true)).willReturn(UPLOADED_IMAGE_PATH);
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
    @DisplayName("라이브니스 성공 시 이력이 success 로 갱신되고 응답 값이 그대로 반환된다")
    void execute_success() {
        givenCommonFlow();
        given(faceService.liveness(any(LivenessFaceFeignRequestDTO.class)))
                .willReturn(LivenessFaceFeignResponseDTO.builder()
                        .success(true)
                        .probability("0.97")
                        .prdioction(1)
                        .prdioctionDesc("live")
                        .build());

        LivenessResult result = livenessFaceUseCase.execute(input);

        assertThat(result.success()).isTrue();
        assertThat(result.probability()).isEqualTo("0.97");
        assertThat(result.transactionUuid()).isEqualTo(TRANSACTION_UUID);

        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isTrue();
        // MatchHistory 가 toPercent() 로 ×100, scale 2 를 적용한다
        assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("97.00"));
    }

    @Test
    @DisplayName("라이브니스 실패 시 prdioctionDesc 가 실패 사유로 대문자 기록된다")
    void execute_livenessFailure() {
        givenCommonFlow();
        given(faceService.liveness(any(LivenessFaceFeignRequestDTO.class)))
                .willReturn(LivenessFaceFeignResponseDTO.builder()
                        .success(false)
                        .probability("0.12")
                        .prdioctionDesc("spoof")
                        .build());

        livenessFaceUseCase.execute(input);

        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getFailureType()).isEqualTo("SPOOF");
    }

    @Test
    @DisplayName("UG-280 3차: 200 인데 prdioctionDesc 가 null 이어도 NPE 없이 이력이 남는다")
    void execute_nullPrdioctionDesc_survives() {
        // 예전에는 data.getPrdioctionDesc().toUpperCase() 가 곧바로 NPE 를 냈다. NPE 는
        // noRollbackFor 에 걸리지 않으므로 REQUIRES_NEW 가 롤백되고, 바로 위에서 저장한 이력
        // 행이 통째로 사라졌다 — 2차 리뷰가 잡은 .getFaceId() NPE 와 같은 결함 형태다.
        // palm 에는 이 가드가 이미 있었고 face 만 무방비였다.
        givenCommonFlow();
        given(faceService.liveness(any(LivenessFaceFeignRequestDTO.class)))
                .willReturn(LivenessFaceFeignResponseDTO.builder()
                        .success(false)
                        .probability("0.12")
                        .prdioctionDesc(null)
                        .build());

        assertThatCode(() -> livenessFaceUseCase.execute(input)).doesNotThrowAnyException();

        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getFailureType())
                .as("사유를 못 읽어도 행은 남아야 한다 — 사유 없는 행이 행 없음보다 낫다")
                .isEqualTo("LIVENESS_FAILED");
    }

    @Test
    @DisplayName("UG-280 3차: probability 가 숫자가 아니어도 NumberFormatException 으로 이력이 사라지지 않는다")
    void execute_nonNumericProbability_survives() {
        givenCommonFlow();
        given(faceService.liveness(any(LivenessFaceFeignRequestDTO.class)))
                .willReturn(LivenessFaceFeignResponseDTO.builder()
                        .success(true)
                        .probability("N/A")
                        .build());

        assertThatCode(() -> livenessFaceUseCase.execute(input)).doesNotThrowAnyException();

        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isTrue();
        assertThat(saved.getSimilarity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("하위 서비스 4xx 는 사유를 남긴 뒤 전파된다")
    void execute_feignError_recordsReasonThenPropagates() {
        givenCommonFlow();
        CustomFeignException exception = new CustomFeignException("ML-101", "FACE_NOT_FOUND", "no face");
        given(faceService.liveness(any(LivenessFaceFeignRequestDTO.class))).willThrow(exception);

        assertThatThrownBy(() -> livenessFaceUseCase.execute(input)).isSameAs(exception);

        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getFailureType()).isEqualTo("FACE_NOT_FOUND");
    }

    @Test
    @DisplayName("하위 서비스 5xx·응답 없음도 사유를 남긴 뒤 전파된다 — 행이 롤백되지 않아야 한다")
    void execute_remoteCallException_recordsReasonThenPropagates() {
        givenCommonFlow();
        RemoteCallException exception = new RemoteCallException(503);
        given(faceService.liveness(any(LivenessFaceFeignRequestDTO.class))).willThrow(exception);

        assertThatThrownBy(() -> livenessFaceUseCase.execute(input)).isSameAs(exception);

        MatchHistory saved = capturedMatchHistory();
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getFailureType()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR.name());
    }
}

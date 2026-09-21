package ai.univs.gate.modules.feature.application.usecase.palm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.palm.DeletePalmFeatureInput;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureActionType;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.feature.domain.repository.FeatureHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.DeletePalmFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.palm.PalmService;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

/**
 * UG-325: 손바닥 특징점 삭제가 이력을 남긴다.
 *
 * <p>이 유스케이스에는 그동안 테스트가 없었다. 예전 동작은 "palm 에 위임하고 {@code is_deleted} 만
 * 켠다" 였고 gate 에는 삭제 기록이 없었다 — 대시보드가 삭제 건수를 등록 시각으로 우회해 세다
 * 시점이 틀린 것도 그 결과다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-325: 손바닥 특징점 삭제 이력")
class DeletePalmFeatureUseCaseTest {

    private static final Long ACCOUNT_ID = 10L;
    private static final Long FEATURE_SEQ = 7L;
    private static final String API_KEY = "gate_test-api-key";
    private static final String FEATURE_ID = "palm-uuid-1";

    @Mock private BiometricFeatureRepository biometricFeatureRepository;
    @Mock private FeatureHistoryRepository featureHistoryRepository;
    @Mock private ApiKeyService apiKeyService;
    @Mock private PalmService palmService;

    @InjectMocks private DeletePalmFeatureUseCase useCase;

    private Project project;
    private ApiKey apiKey;
    private BiometricFeature feature;
    private DeletePalmFeatureInput input;

    @BeforeEach
    void setUp() {
        project = Project.builder()
                .id(1L).accountId(ACCOUNT_ID).projectName("p").branchName("branch-1")
                .status(ProjectStatus.ACTIVE).build();
        apiKey = ApiKey.builder()
                .id(5L).project(project).apiKey(API_KEY).secretKey("s")
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC)).isActive(true).build();
        feature = BiometricFeature.builder()
                .id(FEATURE_SEQ).project(project).type(FeatureType.PALM)
                .featureId(FEATURE_ID).featureImagePath("feature/a.jpg").description("홍길동")
                .externalKey("emp-42").isDeleted(false).build();
        input = new DeletePalmFeatureInput(ACCOUNT_ID, API_KEY, FEATURE_SEQ);

    }

    /** 정상 경로 스텁. strict stubs 라 쓰지 않는 테스트에 깔아 두면 실패한다 — 필요한 곳에서만 부른다. */
    private void 정상_흐름_스텁() {
        given(biometricFeatureRepository.findByIdAndTypeAndIsDeletedFalse(FEATURE_SEQ, FeatureType.PALM))
                .willReturn(Optional.of(feature));
        given(apiKeyService.findOwnedByApiKey(API_KEY, ACCOUNT_ID)).willReturn(apiKey);
        given(featureHistoryRepository.save(any(FeatureHistory.class))).willAnswer(inv -> inv.getArgument(0));
    }

    private FeatureHistory 저장된_이력() {
        ArgumentCaptor<FeatureHistory> captor = ArgumentCaptor.forClass(FeatureHistory.class);
        verify(featureHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("성공: 호출 전에 이력을 저장하고, 성공으로 갱신하며, 스냅샷과 transaction_uuid 가 palm 요청과 일치한다")
    void 성공() {
        정상_흐름_스텁();

        useCase.execute(input);

        // 순서: 이력 저장 → palm 호출. 반대면 palm 이 실패했을 때 남는 것이 없다.
        InOrder order = inOrder(featureHistoryRepository, palmService);
        order.verify(featureHistoryRepository).save(any(FeatureHistory.class));
        order.verify(palmService).deletePalm(any(DeletePalmFeignRequestDTO.class));

        FeatureHistory history = 저장된_이력();
        assertThat(history.getActionType()).isEqualTo(FeatureActionType.DELETE);
        assertThat(history.getFeatureType()).isEqualTo(FeatureType.PALM);
        assertThat(history.isSuccess()).isTrue();
        assertThat(history.getFailureType()).isNull();
        // 스냅샷 — 대상이 나중에 정리돼도 "무엇을 지웠나" 가 남아야 한다
        assertThat(history.getFeatureSeq()).isEqualTo(FEATURE_SEQ);
        assertThat(history.getFeatureId()).isEqualTo(FEATURE_ID);
        assertThat(history.getUserDescription()).isEqualTo("홍길동");
        assertThat(history.getFeatureImagePath()).isEqualTo("feature/a.jpg");
        assertThat(history.getExternalKey()).isEqualTo("emp-42");
        assertThat(history.getProject()).isSameAs(project);

        ArgumentCaptor<DeletePalmFeignRequestDTO> req = ArgumentCaptor.forClass(DeletePalmFeignRequestDTO.class);
        verify(palmService).deletePalm(req.capture());
        assertThat(req.getValue().getBranchName()).isEqualTo("branch-1");
        assertThat(req.getValue().getPalmId()).isEqualTo(FEATURE_ID);
        assertThat(req.getValue().getClientId()).isEqualTo(ACCOUNT_ID.toString());
        assertThat(req.getValue().getTransactionUuid())
                .as("gate 의 feature_history 와 palm 의 palm_history 가 같은 uuid 로 이어져야 한다")
                .isEqualTo(history.getTransactionUuid())
                .isNotBlank();

        assertThat(feature.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("palm 이 비즈니스 오류(CustomFeignException)를 내면 이력은 실패로 남고 대상은 지워지지 않는다")
    void 하위_비즈니스_오류() {
        정상_흐름_스텁();
        CustomFeignException e = new CustomFeignException("PALM-404", "PALM_NOT_FOUND", "not found");
        willThrow(e).given(palmService).deletePalm(any(DeletePalmFeignRequestDTO.class));

        assertThatThrownBy(() -> useCase.execute(input)).isSameAs(e);

        FeatureHistory history = 저장된_이력();
        assertThat(history.isSuccess()).isFalse();
        assertThat(history.getFailureType()).isEqualTo("PALM_NOT_FOUND");
        assertThat(feature.isDeleted()).as("palm 에서 지워지지 않았으니 gate 에서도 살아 있어야 한다").isFalse();
    }

    @Test
    @DisplayName("palm 이 응답을 못 주면(RemoteCallException) 이력은 INTERNAL_SERVER_ERROR 로 남고 대상은 지워지지 않는다")
    void 하위_장애() {
        정상_흐름_스텁();
        RemoteCallException e = new RemoteCallException(RemoteCallException.NO_RESPONSE, "palm.deletePalm", new RuntimeException("timeout"));
        willThrow(e).given(palmService).deletePalm(any(DeletePalmFeignRequestDTO.class));

        assertThatThrownBy(() -> useCase.execute(input)).isSameAs(e);

        FeatureHistory history = 저장된_이력();
        assertThat(history.isSuccess()).isFalse();
        assertThat(history.getFailureType()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR.name());
        assertThat(feature.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("남의 특징점이면 INVALID_USER 로 거부하고 이력도 palm 호출도 없다")
    void 소유_불일치() {
        given(biometricFeatureRepository.findByIdAndTypeAndIsDeletedFalse(FEATURE_SEQ, FeatureType.PALM))
                .willReturn(Optional.of(feature));
        Project other = Project.builder().id(2L).accountId(99L).projectName("o").branchName("b2")
                .status(ProjectStatus.ACTIVE).build();
        ApiKey otherKey = ApiKey.builder().id(6L).project(other).apiKey(API_KEY).secretKey("s")
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC)).isActive(true).build();
        given(apiKeyService.findOwnedByApiKey(API_KEY, ACCOUNT_ID)).willReturn(otherKey);

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(CustomGateException.class)
                .satisfies(t -> assertThat(((CustomGateException) t).getErrorType()).isEqualTo(ErrorType.INVALID_USER));

        // 남의 것을 지우려던 시도는 남기지 않는다 — 남기면 그 행의 project 가 요청자 것도 대상 것도 아니다.
        verify(featureHistoryRepository, never()).save(any(FeatureHistory.class));
        verify(palmService, never()).deletePalm(any(DeletePalmFeignRequestDTO.class));
        assertThat(feature.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("대상이 없거나 이미 지워졌으면 INVALID_USER 이고 아무것도 쓰지 않는다")
    void 대상_없음() {
        given(biometricFeatureRepository.findByIdAndTypeAndIsDeletedFalse(FEATURE_SEQ, FeatureType.PALM))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(input)).isInstanceOf(CustomGateException.class);
        verify(featureHistoryRepository, never()).save(any(FeatureHistory.class));
        verify(palmService, never()).deletePalm(any(DeletePalmFeignRequestDTO.class));
    }

    /**
     * 위 실패 테스트들은 Mockito 라 실제 트랜잭션이 없다 — {@code noRollbackFor} 를 지워도 전부 초록이다.
     * 그런데 그 속성이 이 티켓의 핵심이다: 없으면 palm 실패 시 이력 행이 롤백으로 사라진다 (UG-280).
     * 선언 자체를 못박는다.
     */
    @Test
    @DisplayName("하위 서비스 실패 예외 두 종류가 noRollbackFor 에 선언돼 있다")
    void noRollbackFor_선언() throws NoSuchMethodException {
        Method execute = DeletePalmFeatureUseCase.class.getMethod("execute", DeletePalmFeatureInput.class);
        Transactional tx = execute.getAnnotation(Transactional.class);
        assertThat(tx).as("@Transactional 이 execute 에 있어야 한다").isNotNull();
        assertThat(tx.noRollbackFor()).contains(CustomFeignException.class, RemoteCallException.class);
    }
}

package ai.univs.gate.modules.feature.application.usecase.face;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.support.history.HistoryRecorder;
import ai.univs.gate.modules.feature.application.input.face.DeleteFaceFeatureInput;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureActionType;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.DeleteFaceFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.face.FaceService;
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
import ai.univs.gate.support.tx.RecordingTransactionTemplate;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

/**
 * UG-325: 얼굴 특징점 삭제가 이력을 남긴다.
 *
 * <p>이 유스케이스에는 그동안 테스트가 없었다. 예전 동작은 "face 에 위임하고 {@code is_deleted} 만
 * 켠다" 였고 gate 에는 삭제 기록이 없었다 — 대시보드가 삭제 건수를 등록 시각으로 우회해 세다
 * 시점이 틀린 것도 그 결과다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-325: 얼굴 특징점 삭제 이력")
class DeleteFaceFeatureUseCaseTest {

    private static final Long ACCOUNT_ID = 10L;
    private static final Long FEATURE_SEQ = 7L;
    private static final String API_KEY = "gate_test-api-key";
    private static final String FEATURE_ID = "face-uuid-1";

    @Mock private BiometricFeatureRepository biometricFeatureRepository;
    @Mock private HistoryRecorder historyRecorder;
    @Mock private ApiKeyService apiKeyService;
    @Mock private FaceService faceService;

    // UG-336: 성공 쓰기가 짧은 트랜잭션 안에서 일어난다. null 이면 NPE, 목이면 콜백이 안 돈다.
    @Spy private RecordingTransactionTemplate transactionTemplate = new RecordingTransactionTemplate();

    @InjectMocks private DeleteFaceFeatureUseCase useCase;

    private Project project;
    private ApiKey apiKey;
    private BiometricFeature feature;
    private DeleteFaceFeatureInput input;

    @BeforeEach
    void setUp() {
        project = Project.builder()
                .id(1L).accountId(ACCOUNT_ID).projectName("p").branchName("branch-1")
                .status(ProjectStatus.ACTIVE).build();
        apiKey = ApiKey.builder()
                .id(5L).project(project).apiKey(API_KEY).secretKey("s")
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC)).isActive(true).build();
        feature = BiometricFeature.builder()
                .id(FEATURE_SEQ).project(project).type(FeatureType.FACE)
                .featureId(FEATURE_ID).featureImagePath("feature/a.jpg").description("홍길동")
                .externalKey("emp-42").isDeleted(false).build();
        input = new DeleteFaceFeatureInput(ACCOUNT_ID, API_KEY, FEATURE_SEQ);

    }

    /** 정상 경로 스텁. strict stubs 라 쓰지 않는 테스트에 깔아 두면 실패한다 — 필요한 곳에서만 부른다. */
    private void 정상_흐름_스텁() {
        given(biometricFeatureRepository.findByIdAndTypeAndIsDeletedFalse(FEATURE_SEQ, FeatureType.FACE))
                .willReturn(Optional.of(feature));
        given(apiKeyService.findOwnedByApiKey(API_KEY, ACCOUNT_ID)).willReturn(apiKey);
        given(historyRecorder.start(any(FeatureHistory.class))).willAnswer(inv -> inv.getArgument(0));
    }

    private FeatureHistory 저장된_이력() {
        ArgumentCaptor<FeatureHistory> captor = ArgumentCaptor.forClass(FeatureHistory.class);
        verify(historyRecorder).start(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("성공: 호출 전에 이력을 저장하고, 성공으로 갱신하며, 스냅샷과 transaction_uuid 가 face 요청과 일치한다")
    void 성공() {
        정상_흐름_스텁();

        useCase.execute(input);

        // 순서: 이력 저장 → face 호출. 반대면 face 가 실패했을 때 남는 것이 없다.
        InOrder order = inOrder(historyRecorder, faceService);
        order.verify(historyRecorder).start(any(FeatureHistory.class));
        order.verify(faceService).deleteFace(any(DeleteFaceFeignRequestDTO.class));

        FeatureHistory history = 저장된_이력();
        assertThat(history.getActionType()).isEqualTo(FeatureActionType.DELETE);
        assertThat(history.getFeatureType()).isEqualTo(FeatureType.FACE);
        assertThat(history.isSuccess()).isTrue();
        assertThat(history.getFailureType()).isNull();
        // 스냅샷 — 대상이 나중에 정리돼도 "무엇을 지웠나" 가 남아야 한다
        assertThat(history.getFeatureSeq()).isEqualTo(FEATURE_SEQ);
        assertThat(history.getFeatureId()).isEqualTo(FEATURE_ID);
        assertThat(history.getUserDescription()).isEqualTo("홍길동");
        assertThat(history.getFeatureImagePath()).isEqualTo("feature/a.jpg");
        assertThat(history.getExternalKey()).isEqualTo("emp-42");
        assertThat(history.getProject()).isSameAs(project);

        ArgumentCaptor<DeleteFaceFeignRequestDTO> req = ArgumentCaptor.forClass(DeleteFaceFeignRequestDTO.class);
        verify(faceService).deleteFace(req.capture());
        assertThat(req.getValue().getBranchName()).isEqualTo("branch-1");
        assertThat(req.getValue().getFaceId()).isEqualTo(FEATURE_ID);
        assertThat(req.getValue().getClientId()).isEqualTo(ACCOUNT_ID.toString());
        assertThat(req.getValue().getTransactionUuid())
                .as("gate 의 feature_history 와 face 의 face_history 가 같은 uuid 로 이어져야 한다")
                .isEqualTo(history.getTransactionUuid())
                .isNotBlank();

        assertThat(feature.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("face 가 비즈니스 오류(CustomFeignException)를 내면 이력은 실패로 남고 대상은 지워지지 않는다")
    void 하위_비즈니스_오류() {
        정상_흐름_스텁();
        CustomFeignException e = new CustomFeignException("FACE-404", "FACE_NOT_FOUND", "not found");
        willThrow(e).given(faceService).deleteFace(any(DeleteFaceFeignRequestDTO.class));

        assertThatThrownBy(() -> useCase.execute(input)).isSameAs(e);

        FeatureHistory history = 저장된_이력();
        assertThat(history.isSuccess()).isFalse();
        assertThat(history.getFailureType()).isEqualTo("FACE_NOT_FOUND");
        assertThat(feature.isDeleted()).as("face 에서 지워지지 않았으니 gate 에서도 살아 있어야 한다").isFalse();
    }

    @Test
    @DisplayName("face 가 응답을 못 주면(RemoteCallException) 이력은 INTERNAL_SERVER_ERROR 로 남고 대상은 지워지지 않는다")
    void 하위_장애() {
        정상_흐름_스텁();
        RemoteCallException e = new RemoteCallException(RemoteCallException.NO_RESPONSE, "face.deleteFace", new RuntimeException("timeout"));
        willThrow(e).given(faceService).deleteFace(any(DeleteFaceFeignRequestDTO.class));

        assertThatThrownBy(() -> useCase.execute(input)).isSameAs(e);

        FeatureHistory history = 저장된_이력();
        assertThat(history.isSuccess()).isFalse();
        assertThat(history.getFailureType()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR.name());
        assertThat(feature.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("남의 특징점이면 INVALID_USER 로 거부하고 이력도 face 호출도 없다")
    void 소유_불일치() {
        given(biometricFeatureRepository.findByIdAndTypeAndIsDeletedFalse(FEATURE_SEQ, FeatureType.FACE))
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
        verify(historyRecorder, never()).start(any(FeatureHistory.class));
        verify(faceService, never()).deleteFace(any(DeleteFaceFeignRequestDTO.class));
        assertThat(feature.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("대상이 없거나 이미 지워졌으면 INVALID_USER 이고 아무것도 쓰지 않는다")
    void 대상_없음() {
        given(biometricFeatureRepository.findByIdAndTypeAndIsDeletedFalse(FEATURE_SEQ, FeatureType.FACE))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(input)).isInstanceOf(CustomGateException.class);
        verify(historyRecorder, never()).start(any(FeatureHistory.class));
        verify(faceService, never()).deleteFace(any(DeleteFaceFeignRequestDTO.class));
    }

    /**
     * 위 실패 테스트들은 Mockito 라 실제 트랜잭션이 없다 — 이력 행이 정말 남는지는 보지 못한다.
     *
     * <p>예전에는 이 자리에 {@code noRollbackFor} <b>선언</b>을 못박는 테스트가 있었다. 선언을
     * 지우면 face 실패 시 이력이 롤백으로 사라졌기 때문이다. UG-293 이 그 구조를 바꿔 이력을
     * 호출자 트랜잭션 밖에서 커밋하므로 선언 자체가 사라졌고, 이 테스트도 함께 폐기했다.
     *
     * <p>대신 {@code HistoryRecorderSliceTest} 가 실제 트랜잭션을 열고 롤백시켜 <b>행이 남는지</b>
     * 를 직접 본다 — 선언이 아니라 동작을 보므로, 열거하지 않은 예외에서도 성립한다.
     */

    /**
     * <b>다른 영속성 컨텍스트에서 온 같은 프로젝트면 지운다</b> (UG-336).
     *
     * <p>이 유스케이스에서 트랜잭션을 떼면서 특징점과 API 키가 <b>서로 다른 컨텍스트</b>에서
     * 온다. {@code Project} 는 {@code equals} 를 재정의하지 않으므로, 소유 확인이 인스턴스
     * 비교로 남아 있으면 <b>모든 삭제가 INVALID_USER 로 거부된다.</b>
     *
     * <p>나머지 테스트는 특징점과 키가 <b>같은 객체</b>를 공유해서 이 회귀를 잡지 못한다. 여기서는
     * id 만 같은 별개 인스턴스를 쓴다.
     */
    @Test
    @DisplayName("UG-336: 다른 컨텍스트에서 온 같은 프로젝트면 지운다 — 인스턴스가 아니라 id 로 비교한다")
    void 다른_컨텍스트의_같은_프로젝트면_지운다() {
        Project 같은_프로젝트_다른_객체 = Project.builder()
                .id(project.getId()).accountId(ACCOUNT_ID).projectName("p").branchName("branch-1")
                .isDeleted(false).status(project.getStatus()).build();
        BiometricFeature 다른_컨텍스트의_특징점 = BiometricFeature.builder()
                .id(FEATURE_SEQ).project(같은_프로젝트_다른_객체).type(FeatureType.FACE)
                .featureId(FEATURE_ID).isDeleted(false).build();
        assertThat(다른_컨텍스트의_특징점.getProject()).isNotSameAs(project);

        given(biometricFeatureRepository.findByIdAndTypeAndIsDeletedFalse(FEATURE_SEQ, FeatureType.FACE))
                .willReturn(Optional.of(다른_컨텍스트의_특징점));
        given(apiKeyService.findOwnedByApiKey(API_KEY, ACCOUNT_ID)).willReturn(apiKey);
        given(historyRecorder.start(any(FeatureHistory.class))).willAnswer(inv -> inv.getArgument(0));

        useCase.execute(input);

        assertThat(다른_컨텍스트의_특징점.isDeleted()).isTrue();
    }

    /**
     * <b>소프트 삭제와 성공 이력만 트랜잭션 안에서 일어난다</b> (UG-336).
     *
     * <p>이 유스케이스의 목적이 이것이다. 시작 이력({@code REQUIRES_NEW})이 바깥 트랜잭션
     * 안에서 불리면 커넥션을 두 개 쥐고, 원격 호출이 트랜잭션 안에 있으면 그동안 커넥션 하나가
     * 묶인다. 반대로 소프트 삭제와 성공 이력이 경계 <b>밖</b>으로 새면 따로 커밋돼
     * "지워지지 않았는데 삭제 성공 이력만 있는" 상태가 가능해진다.
     *
     * <p>"둘 다 호출됐다" 만 보면 어느 쪽 회귀도 잡지 못한다. 호출 <b>시점</b>에 경계 안이었는지를
     * 기록한다.
     */
    @Test
    @DisplayName("UG-336: 시작 이력·원격 호출은 트랜잭션 밖, 소프트 삭제·성공 이력은 안이다")
    void 성공_쓰기만_트랜잭션_안이다() {
        java.util.List<String> 기록 = new java.util.ArrayList<>();
        given(biometricFeatureRepository.findByIdAndTypeAndIsDeletedFalse(FEATURE_SEQ, FeatureType.FACE))
                .willAnswer(inv -> { 기록.add("조회:" + transactionTemplate.isActive()); return Optional.of(feature); });
        given(apiKeyService.findOwnedByApiKey(API_KEY, ACCOUNT_ID)).willReturn(apiKey);
        given(historyRecorder.start(any(FeatureHistory.class)))
                .willAnswer(inv -> { 기록.add("start:" + transactionTemplate.isActive()); return inv.getArgument(0); });
        willAnswer(inv -> { 기록.add("원격:" + transactionTemplate.isActive()); return null; })
                .given(faceService).deleteFace(any(DeleteFaceFeignRequestDTO.class));
        willAnswer(inv -> { 기록.add("succeed:" + transactionTemplate.isActive()); return null; })
                .given(historyRecorder).succeed(any(FeatureHistory.class));

        useCase.execute(input);

        assertThat(기록).containsExactly(
                "조회:false",     // 소유 확인용 — 경계 밖
                "start:false",    // REQUIRES_NEW 가 바깥 트랜잭션 없이 → 커넥션 하나
                "원격:false",     // 원격 호출 동안 커넥션을 쥐지 않는다
                "조회:true",      // 성공 트랜잭션 안에서 다시 읽어 소프트 삭제
                "succeed:true");  // 소프트 삭제와 같은 트랜잭션
        assertThat(feature.isDeleted()).isTrue();
        assertThat(transactionTemplate.executions()).as("경계는 성공 블록 하나뿐이다").isEqualTo(1);
    }
}

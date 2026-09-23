package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.support.history.HistoryRecorder;
import ai.univs.gate.modules.feature.application.input.face.IdentifyByDescriptorInput;
import ai.univs.gate.modules.feature.application.input.face.VerifyByDescriptorInput;
import ai.univs.gate.modules.feature.application.result.face.IdentifyByDescriptorResult;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.CreateFaceByDescriptorFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.IdentifyFaceByDescriptorFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.MatchFaceFeignResponseDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.VerifyFaceByDescriptorFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.VerifyFaceByDescriptorFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.face.FaceFeatureService;
import ai.univs.gate.support.feature.face.FaceService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import ai.univs.gate.support.tx.RecordingTransactionTemplate;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * UG-279: descriptor 경로가 매칭 이력에 남기는 값을 검증한다.
 *
 * <p>반박 리뷰에서 이 구간의 뮤테이션 4개가 <b>어떤 테스트도 죽이지 못한 채 생존</b>했다.
 * 컨트롤러 라우팅·apiKey 전파·descriptor 검증만 덮여 있었고, 정작 이력 전이는 0줄이었다.
 * 생존한 뮤테이션은 다음과 같고, 이 클래스가 각각을 잡는다.
 * <ul>
 *   <li>{@code IdentifyByDescriptorUseCase} 의 {@code checkLiveness(false)} → {@code true}
 *       — "라이브니스 무조건 OFF" 는 이 티켓의 핵심 계약이다.</li>
 *   <li>{@code FaceFeatureService.createFaceFeatureByDescriptor} 의 같은 지점.</li>
 *   <li>{@code VerifyByDescriptorUseCase} 의 {@code VERIFY_DESCRIPTOR} → {@code VERIFY_IMAGE}
 *       — 매칭 타입이 바뀌면 이력 집계가 조용히 이미지 기반과 섞인다.</li>
 * </ul>
 *
 * <p>추가로 리뷰에서 확인된 결함 두 건의 회귀도 여기서 막는다 — Feign 실패 시 실패 사유
 * 미기록, 1:1 실패 코드가 이미지 기반과 불일치({@code NOT_MATCH} vs {@code MISMATCH}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-279: descriptor 경로의 매칭 이력")
class DescriptorMatchHistoryTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final Long SAVED_ID = 100L;
    private static final String API_KEY = "gate_test-api-key";
    private static final String TX = "550e8400-e29b-41d4-a716-446655440000";
    private static final String DESCRIPTOR = "descriptor-base64";

    private Project project;
    private ApiKey apiKey;

    @BeforeEach
    void 픽스처() {
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
    }

    private static MatchHistory captureSaved(HistoryRecorder repository) {
        ArgumentCaptor<MatchHistory> captor = ArgumentCaptor.forClass(MatchHistory.class);
        verify(repository).start(captor.capture());
        return captor.getValue();
    }

    private static void stubSave(HistoryRecorder repository) {
        given(repository.start(any(MatchHistory.class))).willAnswer(invocation -> {
            MatchHistory saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", SAVED_ID);
            return saved;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("1:N 매칭")
    class 매칭 {

        @Mock private HistoryRecorder historyRecorder;
        @Mock private ProjectSettingsService projectSettingsService;
        @Mock private FaceFeatureService faceFeatureService;
        @Mock private ApiKeyService apiKeyService;
        @Mock private FaceService faceService;

        @InjectMocks private IdentifyByDescriptorUseCase useCase;

        private final IdentifyByDescriptorInput input =
                new IdentifyByDescriptorInput(ACCOUNT_ID, API_KEY, DESCRIPTOR, TX);

        private void 공통() {
            given(apiKeyService.findOwnedByApiKey(API_KEY, ACCOUNT_ID)).willReturn(apiKey);
            given(projectSettingsService.findByProject(project)).willReturn(
                    ProjectSettings.builder().id(2L).project(project).consentEnabled(true).build());
            stubSave(historyRecorder);
        }

        @Test
        @DisplayName("성공 — matchType 은 IDENTIFY, checkLiveness 는 프로젝트 설정과 무관하게 false")
        void 성공() {
            공통();
            given(faceService.identifyByDescriptor(any(IdentifyFaceByDescriptorFeignRequestDTO.class)))
                    .willReturn(MatchFaceFeignResponseDTO.builder()
                            .transactionUuid(TX)
                            .faceId("registered-face-id")
                            .similarity(new BigDecimal("0.87"))
                            .result(true)
                            .build());
            given(faceFeatureService.getFaceFeatureByFaceIdAndProjectId("registered-face-id", PROJECT_ID))
                    .willReturn(BiometricFeature.builder()
                            .id(7L).project(project).type(FeatureType.FACE)
                            .featureId("registered-face-id").isDeleted(false).build());

            IdentifyByDescriptorResult result = useCase.execute(input);

            assertThat(result.matchingHistoryId()).isEqualTo(SAVED_ID);
            assertThat(result.projectId()).isEqualTo(PROJECT_ID);
            assertThat(result.matchType()).isEqualTo(MatchType.IDENTIFY);
            assertThat(result.success()).isTrue();
            assertThat(result.featureId()).isEqualTo("registered-face-id");
            assertThat(result.similarity()).isEqualTo(new BigDecimal("87.00"));
            assertThat(result.failureType()).isEmpty();
            assertThat(result.transactionUuid()).isEqualTo(TX);

            MatchHistory saved = captureSaved(historyRecorder);
            assertThat(saved.getMatchType()).isEqualTo(MatchType.IDENTIFY);
            assertThat(saved.getFeatureType()).isEqualTo(FeatureType.FACE);
            assertThat(saved.getCheckLiveness())
                    .as("descriptor 경로는 라이브니스를 수행하지 않는다 — 이력에 true 가 남으면 계약 위반")
                    .isFalse();
            assertThat(saved.getConsentSnapshot()).isTrue();
            assertThat(saved.getSuccess()).isTrue();
        }

        @Test
        @DisplayName("유사도 미달 — NOT_MATCH 로 기록하고 실패 결과를 반환한다")
        void 불일치() {
            공통();
            given(faceService.identifyByDescriptor(any(IdentifyFaceByDescriptorFeignRequestDTO.class)))
                    .willReturn(MatchFaceFeignResponseDTO.builder()
                            .transactionUuid(TX).similarity(new BigDecimal("0.10")).result(false).build());

            IdentifyByDescriptorResult result = useCase.execute(input);

            assertThat(result.success()).isFalse();
            assertThat(result.featureId()).isEmpty();
            assertThat(result.failureType()).isEqualTo(ErrorType.NOT_MATCH.name());

            MatchHistory saved = captureSaved(historyRecorder);
            assertThat(saved.getFailureType()).isEqualTo(ErrorType.NOT_MATCH.name());
            assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("10.00"));
            assertThat(saved.getCheckLiveness()).isFalse();
            verifyNoInteractions(faceFeatureService);
        }

        @Test
        @DisplayName("매칭됐지만 gate 에 특징점이 없으면 그 사유를 기록한다")
        void 특징점_없음() {
            공통();
            given(faceService.identifyByDescriptor(any(IdentifyFaceByDescriptorFeignRequestDTO.class)))
                    .willReturn(MatchFaceFeignResponseDTO.builder()
                            .transactionUuid(TX).faceId("unknown").similarity(new BigDecimal("0.90")).result(true).build());
            given(faceFeatureService.getFaceFeatureByFaceIdAndProjectId("unknown", PROJECT_ID))
                    .willThrow(new CustomGateException(ErrorType.INVALID_USER));

            IdentifyByDescriptorResult result = useCase.execute(input);

            assertThat(result.success()).isFalse();
            assertThat(result.failureType()).isEqualTo(ErrorType.INVALID_USER.name());
        }

        @Test
        @DisplayName("Feign 실패 — 실패 사유를 이력에 남긴 뒤 예외를 그대로 전파한다")
        void feign_실패() {
            공통();
            given(faceService.identifyByDescriptor(any(IdentifyFaceByDescriptorFeignRequestDTO.class)))
                    .willThrow(new CustomFeignException(ErrorType.FACE_NOT_FOUND.getCode(), ErrorType.FACE_NOT_FOUND.name(), "no face"));

            assertThatThrownBy(() -> useCase.execute(input)).isInstanceOf(CustomFeignException.class);

            MatchHistory saved = captureSaved(historyRecorder);
            assertThat(saved.getFailureType())
                    .as("failure_type 이 비면 이력 목록에서 '왜 실패했는지 알 수 없는 행' 이 된다")
                    .isEqualTo(ErrorType.FACE_NOT_FOUND.name());
            assertThat(saved.getSuccess()).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("등록")
    class 등록 {

        @Mock private BiometricFeatureRepository biometricFeatureRepository;
        @Mock private HistoryRecorder historyRecorder;
        @Mock private ApiKeyService apiKeyService;
        @Mock private FileService fileService;
        @Mock private FaceService faceService;
        @Mock private ProjectSettingsService projectSettingsService;

        // UG-336: 성공 쓰기가 짧은 트랜잭션 안에서 일어난다. null 이면 NPE, 목이면 콜백이 안 돈다.
        @Spy private RecordingTransactionTemplate transactionTemplate = new RecordingTransactionTemplate();

        @InjectMocks private FaceFeatureService faceFeatureService;

        private void 공통() {
            공통_start_없이();
            given(historyRecorder.start(any(FeatureHistory.class))).willAnswer(inv -> inv.getArgument(0));
        }

        /** 시작 이력 스텁을 테스트가 직접 거는 경우. strict stubs 라 겹쳐 걸 수 없다. */
        private void 공통_start_없이() {
            given(apiKeyService.findOwnedByApiKey(API_KEY, ACCOUNT_ID)).willReturn(apiKey);
            given(projectSettingsService.findByProject(project)).willReturn(
                    ProjectSettings.builder().id(2L).project(project).consentEnabled(true).build());
        }

        /**
         * <b>특징점 저장과 성공 이력만 트랜잭션 안에서 일어난다</b> (UG-336).
         *
         * <p>반박 리뷰가 이 경로에만 경계 테스트가 없다는 것을 변이로 보였다 — 성공 이력을 템플릿
         * 밖으로 빼도, 템플릿을 통째로 없애도(= 저장과 성공 이력이 따로 커밋돼도) 스위트가
         * 초록이었다. face·palm 이미지 등록과 같은 단언을 둔다.
         */
        @Test
        @DisplayName("UG-336: 시작 이력·원격 호출은 트랜잭션 밖, 특징점 저장·성공 이력은 안이다")
        void 성공_쓰기만_트랜잭션_안이다() {
            공통_start_없이();
            java.util.List<String> 기록 = new java.util.ArrayList<>();
            given(historyRecorder.start(any(FeatureHistory.class)))
                    .willAnswer(inv -> { 기록.add("start:" + transactionTemplate.isActive()); return inv.getArgument(0); });
            given(faceService.createFaceByDescriptor(any(CreateFaceByDescriptorFeignRequestDTO.class)))
                    .willAnswer(inv -> { 기록.add("원격:" + transactionTemplate.isActive()); return "issued-face-id"; });
            given(biometricFeatureRepository.save(any(BiometricFeature.class)))
                    .willAnswer(inv -> { 기록.add("save:" + transactionTemplate.isActive()); return inv.getArgument(0); });
            org.mockito.BDDMockito.willAnswer(inv -> { 기록.add("succeed:" + transactionTemplate.isActive()); return null; })
                    .given(historyRecorder).succeed(any(FeatureHistory.class));

            faceFeatureService.createFaceFeatureByDescriptor(ACCOUNT_ID, API_KEY, DESCRIPTOR, TX, null);

            assertThat(기록).containsExactly("start:false", "원격:false", "save:true", "succeed:true");
            assertThat(transactionTemplate.executions()).as("경계는 성공 블록 하나뿐이다").isEqualTo(1);
        }

        private FeatureHistory 저장된_특징점_이력() {
            ArgumentCaptor<FeatureHistory> c = ArgumentCaptor.forClass(FeatureHistory.class);
            verify(historyRecorder).start(c.capture());
            return c.getValue();
        }

        @Test
        @DisplayName("성공 — feature_history REGISTER, checkLiveness=false, 파일 업로드 미호출")
        void 성공() {
            공통();
            given(faceService.createFaceByDescriptor(any(CreateFaceByDescriptorFeignRequestDTO.class)))
                    .willReturn("issued-face-id");

            BiometricFeature feature = faceFeatureService.createFaceFeatureByDescriptor(
                    ACCOUNT_ID, API_KEY, DESCRIPTOR, TX, " cust-77 ");

            assertThat(feature.getFeatureId()).isEqualTo("issued-face-id");
            assertThat(feature.getExternalKey()).as("UG-333: descriptor 경로도 외부 키를 저장한다").isEqualTo("cust-77");
            assertThat(feature.getFeatureImagePath()).isNull();
            assertThat(feature.getDescription()).isNull();
            assertThat(feature.getTransactionUuid()).isEqualTo(TX);
            verify(biometricFeatureRepository).save(feature);

            // UG-325/326: REGISTER 는 feature_history 에만 남는다 — descriptor 등록은 이미지·라이브니스가 없다
            FeatureHistory fh = 저장된_특징점_이력();
            assertThat(fh.isSuccess()).isTrue();
            assertThat(fh.isCheckLiveness()).isFalse();
            assertThat(fh.getFeatureImagePath()).isNull();
            assertThat(fh.getFeatureId()).isEqualTo("issued-face-id");
            assertThat(fh.getConsentSnapshot()).isTrue();

            verifyNoInteractions(fileService);
        }

        @Test
        @DisplayName("응답 없음(RemoteCallException) — INTERNAL_SERVER_ERROR 사유를 남기고 특징점은 저장하지 않는다")
        void 장애() {
            공통();
            given(faceService.createFaceByDescriptor(any(CreateFaceByDescriptorFeignRequestDTO.class)))
                    .willThrow(new RemoteCallException(RemoteCallException.NO_RESPONSE, "face.createFaceByDescriptor", new RuntimeException("reset")));

            assertThatThrownBy(() -> faceFeatureService.createFaceFeatureByDescriptor(ACCOUNT_ID, API_KEY, DESCRIPTOR, TX, null))
                    .isInstanceOf(RemoteCallException.class);

            assertThat(저장된_특징점_이력().getFailureType()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR.name());
            verify(biometricFeatureRepository, never()).save(any());
        }

        @Test
        @DisplayName("Feign 실패 — 실패 사유를 남기고 특징점은 저장하지 않는다")
        void feign_실패() {
            공통();
            given(faceService.createFaceByDescriptor(any(CreateFaceByDescriptorFeignRequestDTO.class)))
                    .willThrow(new CustomFeignException(ErrorType.FACE_NOT_FOUND.getCode(), ErrorType.FACE_NOT_FOUND.name(), "no face"));

            assertThatThrownBy(() -> faceFeatureService.createFaceFeatureByDescriptor(
                    ACCOUNT_ID, API_KEY, DESCRIPTOR, TX, null))
                    .isInstanceOf(CustomFeignException.class);

            assertThat(저장된_특징점_이력().getFailureType())
                    .as("UG-325/326: 실패 사유는 feature_history 에 남는다")
                    .isEqualTo(ErrorType.FACE_NOT_FOUND.name());
            verify(biometricFeatureRepository, never()).save(any());
            verifyNoInteractions(fileService);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("1:1 확인")
    class 확인 {

        @Mock private ApiKeyService apiKeyService;
        @Mock private FaceService faceService;
        @Mock private HistoryRecorder historyRecorder;

        @InjectMocks private VerifyByDescriptorUseCase useCase;

        private final VerifyByDescriptorInput input =
                new VerifyByDescriptorInput(ACCOUNT_ID, API_KEY, DESCRIPTOR, "target-descriptor", TX);

        private void 공통() {
            given(apiKeyService.findOwnedByApiKey(API_KEY, ACCOUNT_ID)).willReturn(apiKey);
            stubSave(historyRecorder);
        }

        private static VerifyFaceByDescriptorFeignResponseDTO 응답(String similarity, boolean result) {
            return VerifyFaceByDescriptorFeignResponseDTO.builder()
                    .transactionUuid(TX).similarity(similarity).result(result).build();
        }

        @Test
        @DisplayName("성공 — VERIFY_DESCRIPTOR 이력, 응답 계약은 face 원값 그대로")
        void 성공() {
            공통();
            given(faceService.verifyDescriptor(any(VerifyFaceByDescriptorFeignRequestDTO.class)))
                    .willReturn(응답("0.98230", true));

            var result = useCase.execute(input);

            // UG-283: 응답 구조를 descriptor 1:N 과 동일하게 맞췄다. face 원값 문자열이 아니라
            // MatchHistory 의 백분율을 내보낸다.
            assertThat(result.matchingHistoryId()).isEqualTo(SAVED_ID);
            assertThat(result.projectId()).isEqualTo(PROJECT_ID);
            assertThat(result.matchType()).isEqualTo(MatchType.VERIFY_DESCRIPTOR);
            assertThat(result.success()).isTrue();
            assertThat(result.similarity()).isEqualTo(new BigDecimal("98.23"));
            assertThat(result.failureType()).isEmpty();
            assertThat(result.transactionUuid()).isEqualTo(TX);
            assertThat(result.featureId())
                    .as("1:1 은 갤러리를 조회하지 않으므로 특정할 등록 사용자가 없다")
                    .isEmpty();

            MatchHistory saved = captureSaved(historyRecorder);
            assertThat(saved.getMatchType())
                    .as("VERIFY_IMAGE 등으로 바뀌면 특징점 1:1 트래픽이 이미지 기반 지표에 섞인다")
                    .isEqualTo(MatchType.VERIFY_DESCRIPTOR);
            assertThat(saved.getFeatureType()).isEqualTo(FeatureType.FACE);
            assertThat(saved.getCheckLiveness()).isFalse();
            assertThat(saved.getSuccess()).isTrue();
            // 이력에는 백분율로 저장된다 (이미지 기반과 같은 스케일).
            assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("98.23"));
            // 1:1 은 성공해도 등록 사용자를 특정하지 않는다.
            assertThat(saved.getFeatureId()).isNull();
            assertThat(saved.getConsentSnapshot())
                    .as("보관할 이미지가 없어 스냅샷할 동의가 없다. settings 조회를 넣으면 "
                            + "project_settings 행이 없는 프로젝트에서 기존 요청이 PJ-106 으로 깨진다")
                    .isNull();
        }

        @Test
        @DisplayName("불일치 — 이미지 기반 1:1 과 같은 MISMATCH 로 기록한다")
        void 불일치() {
            공통();
            given(faceService.verifyDescriptor(any(VerifyFaceByDescriptorFeignRequestDTO.class)))
                    .willReturn(응답("0.10000", false));

            var result = useCase.execute(input);

            assertThat(result.success()).isFalse();
            assertThat(result.similarity()).isEqualTo(new BigDecimal("10.00"));
            assertThat(result.failureType()).isEqualTo(ErrorType.MISMATCH.name());

            MatchHistory saved = captureSaved(historyRecorder);
            assertThat(saved.getFailureType())
                    .as("NOT_MATCH 는 1:N 전용이다. 1:1 실패를 한 조건으로 집계할 수 없게 된다")
                    .isEqualTo(ErrorType.MISMATCH.name());
            assertThat(saved.getSimilarity()).isEqualTo(new BigDecimal("10.00"));
            assertThat(saved.getSuccess()).isFalse();
        }

        @Test
        @DisplayName("Feign 실패 — 실패 사유를 남긴 뒤 예외를 전파한다")
        void feign_실패() {
            공통();
            given(faceService.verifyDescriptor(any(VerifyFaceByDescriptorFeignRequestDTO.class)))
                    .willThrow(new CustomFeignException(ErrorType.INVALID_INPUT.getCode(), ErrorType.INVALID_INPUT.name(), "bad descriptor"));

            assertThatThrownBy(() -> useCase.execute(input)).isInstanceOf(CustomFeignException.class);

            assertThat(captureSaved(historyRecorder).getFailureType())
                    .isEqualTo(ErrorType.INVALID_INPUT.name());
        }

        @Test
        @DisplayName("유사도를 해석할 수 없어도 요청은 깨지지 않고 이력에만 NULL 로 남는다")
        void 유사도_파싱_실패() {
            공통();
            given(faceService.verifyDescriptor(any(VerifyFaceByDescriptorFeignRequestDTO.class)))
                    .willReturn(응답("N/A", true));

            var result = useCase.execute(input);

            assertThat(result.similarity())
                    .as("해석 불가한 유사도는 이력과 응답 모두 null 로 나간다 — 요청 자체는 깨지지 않는다")
                    .isNull();
            assertThat(captureSaved(historyRecorder).getSimilarity()).isNull();
            assertThat(result.success()).isTrue();
        }
    }
}

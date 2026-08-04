package ai.univs.gate.modules.feature.api.controller;

import ai.univs.gate.facade.feature.api.dto.FeatureSelectCondition;
import ai.univs.gate.facade.feature.application.usecase.GetFeatureListUseCase;
import ai.univs.gate.modules.feature.api.dto.CreateFeatureRequestDTO;
import ai.univs.gate.modules.feature.api.dto.face.ExtractRequestDTO;
import ai.univs.gate.modules.feature.api.dto.face.FaceFeatureSelectCondition;
import ai.univs.gate.modules.feature.api.dto.face.IdentifyRequestDTO;
import ai.univs.gate.modules.feature.api.dto.face.LivenessRequestDTO;
import ai.univs.gate.modules.feature.api.dto.face.UpdateFaceFeatureRequestDTO;
import ai.univs.gate.modules.feature.api.dto.face.VerifyByDescriptorRequestDTO;
import ai.univs.gate.modules.feature.api.dto.face.VerifyByFaceIdRequestDTO;
import ai.univs.gate.modules.feature.api.dto.face.VerifyByImageRequestDTO;
import ai.univs.gate.modules.feature.api.dto.match.MatchingHistorySelectCondition;
import ai.univs.gate.modules.feature.api.dto.palm.CreatePalmFeatureRequestDTO;
import ai.univs.gate.modules.feature.api.dto.palm.PalmFeatureSelectCondition;
import ai.univs.gate.modules.feature.api.dto.palm.PalmIdentifyRequestDTO;
import ai.univs.gate.modules.feature.api.dto.palm.PalmLivenessRequestDTO;
import ai.univs.gate.modules.feature.api.dto.palm.UpdatePalmFeatureRequestDTO;
import ai.univs.gate.modules.feature.application.usecase.face.CreateFaceFeatureUseCase;
import ai.univs.gate.modules.feature.application.usecase.face.DeleteFaceFeatureUseCase;
import ai.univs.gate.modules.feature.application.usecase.face.ExtractUseCase;
import ai.univs.gate.modules.feature.application.usecase.face.FaceVerifyByFeatureIdUseCase;
import ai.univs.gate.modules.feature.application.usecase.face.FaceVerifyByFeatureImageUseCase;
import ai.univs.gate.modules.feature.application.usecase.face.GetFaceFeatureByFaceIdUseCase;
import ai.univs.gate.modules.feature.application.usecase.face.GetFaceFeatureUseCase;
import ai.univs.gate.modules.feature.application.usecase.face.GetFaceFeaturesUseCase;
import ai.univs.gate.modules.feature.application.usecase.face.IdentifyFaceUseCase;
import ai.univs.gate.modules.feature.application.usecase.face.LivenessFaceUseCase;
import ai.univs.gate.modules.feature.application.usecase.face.UpdateFaceFeatureUseCase;
import ai.univs.gate.modules.feature.application.usecase.face.VerifyByDescriptorUseCase;
import ai.univs.gate.modules.feature.application.usecase.match.GetMatchHistoriesUseCase;
import ai.univs.gate.modules.feature.application.usecase.match.GetMatchHistoryByTransactionUuidUseCase;
import ai.univs.gate.modules.feature.application.usecase.palm.CreatePalmFeatureUseCase;
import ai.univs.gate.modules.feature.application.usecase.palm.DeletePalmFeatureUseCase;
import ai.univs.gate.modules.feature.application.usecase.palm.GetPalmFeatureUseCase;
import ai.univs.gate.modules.feature.application.usecase.palm.GetPalmFeaturesUseCase;
import ai.univs.gate.modules.feature.application.usecase.palm.IdentifyPalmUseCase;
import ai.univs.gate.modules.feature.application.usecase.palm.LivenessPalmUseCase;
import ai.univs.gate.modules.feature.application.usecase.palm.UpdatePalmFeatureUseCase;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.support.message.MessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.lang.reflect.RecordComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

/**
 * UG-274 회귀 방지.
 *
 * <p>얼굴 라이브니스와 1:1 image 검증은 컨트롤러가 apiKey 인자 자리에 {@code ctx.getTimezone()} 을
 * 넘기고 있어서, UseCase 가 {@code findByApiKey("Asia/Seoul")} 을 호출하고 상시
 * API_KEY_NOT_FOUND(PJ-105) 로 실패했다. 두 값 모두 String 이라 컴파일러가 잡지 못했다.
 *
 * <p>기존 UseCase 단위 테스트는 Input 을 직접 만들어 넣기 때문에 이 결함을 원리적으로 잡을 수 없다.
 * 결함은 컨트롤러 → DTO → Input 매핑 구간에만 존재하므로, 그 구간을 실제로 통과시켜야 한다.
 *
 * <p>여기서는 UseCase 를 목으로 두고 {@code execute} 가 <b>전달받은 인자를 그대로 들고 즉시 중단</b>
 * 하도록 만든다({@link Captured}). 그러면 각 UseCase 의 Result 객체를 만들지 않고도 컨트롤러가
 * 무엇을 넘겼는지 볼 수 있어, apiKey 를 실어 보내는 전 엔드포인트를 같은 방식으로 덮을 수 있다.
 *
 * <p>새 엔드포인트가 apiKey 를 UseCase 로 넘기면 여기에도 한 줄 추가할 것.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-274: 컨트롤러가 UseCase 로 넘기는 apiKey 는 항상 UserContext 의 apiKey 여야 한다")
class ApiKeyPropagationTest {

    private static final String API_KEY = "AK-UG274-FROM-CONTEXT";

    /** 버그 당시 apiKey 자리에 들어가던 값. Accept-TimeZone 헤더가 없을 때의 기본값이다. */
    private static final String TIMEZONE = "Asia/Seoul";

    private static final Long ACCOUNT_ID = 42L;

    @Mock private CreateFaceFeatureUseCase createFaceFeatureUseCase;
    @Mock private UpdateFaceFeatureUseCase updateFaceFeatureUseCase;
    @Mock private DeleteFaceFeatureUseCase deleteFaceFeatureUseCase;
    @Mock private GetFaceFeatureUseCase getFaceFeatureUseCase;
    @Mock private GetFaceFeatureByFaceIdUseCase getFaceFeatureByFaceIdUseCase;
    @Mock private GetFaceFeaturesUseCase getFaceFeaturesUseCase;
    @Mock private ExtractUseCase extractUseCase;
    @Mock private FaceVerifyByFeatureIdUseCase faceVerifyByFeatureIdUseCase;
    @Mock private FaceVerifyByFeatureImageUseCase faceVerifyByFeatureImageUseCase;
    @Mock private VerifyByDescriptorUseCase verifyByDescriptorUseCase;
    @Mock private IdentifyFaceUseCase identifyFaceUseCase;
    @Mock private LivenessFaceUseCase livenessFaceUseCase;

    @Mock private CreatePalmFeatureUseCase createPalmFeatureUseCase;
    @Mock private UpdatePalmFeatureUseCase updatePalmFeatureUseCase;
    @Mock private DeletePalmFeatureUseCase deletePalmFeatureUseCase;
    @Mock private GetPalmFeatureUseCase getPalmFeatureUseCase;
    @Mock private GetPalmFeaturesUseCase getPalmFeaturesUseCase;
    @Mock private IdentifyPalmUseCase identifyPalmUseCase;
    @Mock private LivenessPalmUseCase livenessPalmUseCase;

    @Mock private GetMatchHistoriesUseCase getMatchHistoriesUseCase;
    @Mock private GetMatchHistoryByTransactionUuidUseCase getMatchHistoryByTransactionUuidUseCase;

    @Mock private GetFeatureListUseCase getFeatureListUseCase;

    @Mock private MessageService messageService;

    @InjectMocks private FaceController faceController;
    @InjectMocks private PalmController palmController;
    @InjectMocks private MatchController matchController;
    @InjectMocks private ai.univs.gate.facade.feature.api.controller.FeatureController featureController;

    @BeforeEach
    void 컨텍스트_설정() {
        UserContext.set(UserContext.builder()
                .accountId(String.valueOf(ACCOUNT_ID))
                .email("tester@univs.ai")
                .apiKey(API_KEY)
                .timezone(TIMEZONE)
                .build());
    }

    @AfterEach
    void 컨텍스트_해제() {
        UserContext.clear();
    }

    @Nested
    @DisplayName("Face — 이 결함이 실제로 발생했던 컨트롤러")
    class Face {

        @Test
        @DisplayName("라이브니스 (UG-274 재현 지점)")
        void 라이브니스() {
            given(livenessFaceUseCase.execute(any())).willAnswer(captureFirstArg());
            var request = new LivenessRequestDTO(null, "tx-liveness");
            assertApiKeyPropagated(capture(() -> faceController.liveness(request)));
        }

        @Test
        @DisplayName("1:1 확인 — image 기반 (UG-274 재현 지점)")
        void 확인_image() {
            given(faceVerifyByFeatureImageUseCase.execute(any())).willAnswer(captureFirstArg());
            var request = new VerifyByImageRequestDTO(null, null, "tx-verify-image");
            assertApiKeyPropagated(capture(() -> faceController.verifyByImage(request)));
        }

        @Test
        @DisplayName("1:1 확인 — featureId 기반")
        void 확인_id() {
            given(faceVerifyByFeatureIdUseCase.execute(any())).willAnswer(captureFirstArg());
            var request = new VerifyByFaceIdRequestDTO("face-1", null, "tx-verify-id");
            assertApiKeyPropagated(capture(() -> faceController.verifyById(request)));
        }

        @Test
        @DisplayName("1:1 확인 — descriptor 기반")
        void 확인_descriptor() {
            given(verifyByDescriptorUseCase.execute(any())).willAnswer(captureFirstArg());
            var request = new VerifyByDescriptorRequestDTO("d1", "d2", "tx-descriptor");
            assertApiKeyPropagated(capture(() -> faceController.verifyByDescriptor(request)));
        }

        @Test
        @DisplayName("1:N 매칭")
        void 매칭() {
            given(identifyFaceUseCase.execute(any())).willAnswer(captureFirstArg());
            var request = new IdentifyRequestDTO(null, "tx-identify");
            assertApiKeyPropagated(capture(() -> faceController.identify(request)));
        }

        @Test
        @DisplayName("특징점 추출")
        void 추출() {
            given(extractUseCase.execute(any())).willAnswer(captureFirstArg());
            var request = new ExtractRequestDTO(null, "tx-extract");
            assertApiKeyPropagated(capture(() -> faceController.extract(request)));
        }

        @Test
        @DisplayName("등록")
        void 등록() {
            given(createFaceFeatureUseCase.execute(any())).willAnswer(captureFirstArg());
            var request = new CreateFeatureRequestDTO(null, "desc", "tx-create");
            assertApiKeyPropagated(capture(() -> faceController.create(request)));
        }

        @Test
        @DisplayName("수정")
        void 수정() {
            given(updateFaceFeatureUseCase.execute(any())).willAnswer(captureFirstArg());
            var request = new UpdateFaceFeatureRequestDTO(null, "desc", "tx-update");
            assertApiKeyPropagated(capture(() -> faceController.update(1L, request)));
        }

        @Test
        @DisplayName("삭제")
        void 삭제() {
            willAnswer(captureFirstArg()).given(deleteFaceFeatureUseCase).execute(any());
            assertApiKeyPropagated(capture(() -> faceController.delete(1L)));
        }

        @Test
        @DisplayName("단건 조회")
        void 단건조회() {
            given(getFaceFeatureUseCase.execute(any())).willAnswer(captureFirstArg());
            assertApiKeyPropagated(capture(() -> faceController.get(1L)));
        }

        @Test
        @DisplayName("faceId 기반 조회")
        void faceId조회() {
            given(getFaceFeatureByFaceIdUseCase.execute(any())).willAnswer(captureFirstArg());
            assertApiKeyPropagated(capture(() -> faceController.getByFeatureId("face-1")));
        }

        @Test
        @DisplayName("목록 조회")
        void 목록조회() {
            given(getFaceFeaturesUseCase.execute(any())).willAnswer(captureFirstArg());
            var condition = new FaceFeatureSelectCondition(null, 1, 10, null, null, null, null, null);
            assertApiKeyPropagated(capture(() -> faceController.list(condition)));
        }
    }

    @Nested
    @DisplayName("Palm — 같은 계약면, 같은 매핑 패턴")
    class Palm {

        @Test
        @DisplayName("라이브니스")
        void 라이브니스() {
            given(livenessPalmUseCase.execute(any())).willAnswer(captureFirstArg());
            var request = new PalmLivenessRequestDTO(null, "tx-palm-liveness");
            assertApiKeyPropagated(capture(() -> palmController.liveness(request)));
        }

        @Test
        @DisplayName("1:N 매칭")
        void 매칭() {
            given(identifyPalmUseCase.execute(any())).willAnswer(captureFirstArg());
            var request = new PalmIdentifyRequestDTO(null, "tx-palm-identify");
            assertApiKeyPropagated(capture(() -> palmController.identify(request)));
        }

        @Test
        @DisplayName("등록")
        void 등록() {
            given(createPalmFeatureUseCase.execute(any())).willAnswer(captureFirstArg());
            var request = new CreatePalmFeatureRequestDTO(null, "desc", "tx-palm-create", "ext-1");
            assertApiKeyPropagated(capture(() -> palmController.create(request)));
        }

        @Test
        @DisplayName("수정")
        void 수정() {
            given(updatePalmFeatureUseCase.execute(any())).willAnswer(captureFirstArg());
            var request = new UpdatePalmFeatureRequestDTO(null, "desc", "tx-palm-update");
            assertApiKeyPropagated(capture(() -> palmController.update(1L, request)));
        }

        @Test
        @DisplayName("삭제")
        void 삭제() {
            willAnswer(captureFirstArg()).given(deletePalmFeatureUseCase).execute(any());
            assertApiKeyPropagated(capture(() -> palmController.delete(1L)));
        }

        @Test
        @DisplayName("단건 조회")
        void 단건조회() {
            given(getPalmFeatureUseCase.execute(any())).willAnswer(captureFirstArg());
            assertApiKeyPropagated(capture(() -> palmController.get(1L)));
        }

        @Test
        @DisplayName("목록 조회")
        void 목록조회() {
            given(getPalmFeaturesUseCase.execute(any())).willAnswer(captureFirstArg());
            var condition = new PalmFeatureSelectCondition(1, 20, null, null, null, null);
            assertApiKeyPropagated(capture(() -> palmController.list(condition)));
        }
    }

    @Nested
    @DisplayName("Match / 통합 목록")
    class Others {

        @Test
        @DisplayName("매칭 이력 목록 조회")
        void 이력목록() {
            given(getMatchHistoriesUseCase.execute(any())).willAnswer(captureFirstArg());
            var condition = new MatchingHistorySelectCondition(
                    null, null, null, null, 1, 10, null, null, null, null);
            assertApiKeyPropagated(capture(() -> matchController.getView(condition)));
        }

        @Test
        @DisplayName("트랜잭션 UUID 기반 이력 조회 — apiKey 를 첫 인자로 직접 전달")
        void 이력단건() {
            given(getMatchHistoryByTransactionUuidUseCase.execute(any(), any()))
                    .willAnswer(captureFirstArg());
            assertApiKeyPropagated(
                    capture(() -> matchController.getIdViewByTransactionUuid("tx-1")));
        }

        @Test
        @DisplayName("Face+Palm 통합 목록 조회 — apiKey 와 timezone 을 동시에 넘기는 유일한 경로")
        void 통합목록() {
            given(getFeatureListUseCase.execute(any())).willAnswer(captureFirstArg());
            var condition = new FeatureSelectCondition(null, null, 1, 10, null, null, null);
            assertApiKeyPropagated(capture(() -> featureController.list(condition)));
        }
    }

    // ------------------------------------------------------------------
    // 지원 코드
    // ------------------------------------------------------------------

    /**
     * UseCase 가 받은 인자를 실어 던지는 신호용 예외. Result 객체를 만들지 않고 인자만 확인하기 위한
     * 장치이므로 스택트레이스를 남기지 않는다.
     */
    private static final class Captured extends RuntimeException {
        private final transient Object argument;

        private Captured(Object argument) {
            super(null, null, false, false);
            this.argument = argument;
        }
    }

    private static Answer<Object> captureFirstArg() {
        return invocation -> {
            throw new Captured(invocation.getArgument(0));
        };
    }

    private static Object capture(Executable handlerCall) {
        return assertThrows(Captured.class, handlerCall,
                "UseCase 가 호출되지 않았다 — 컨트롤러가 다른 경로로 빠졌는지 확인할 것").argument;
    }

    private static void assertApiKeyPropagated(Object captured) {
        String actual = apiKeyOf(captured);
        assertEquals(API_KEY, actual, () -> String.format(
                "%s 의 apiKey 가 UserContext 의 apiKey 가 아니다 (실제: %s). "
                        + "컨트롤러가 apiKey 자리에 다른 값을 넘기고 있다 — UG-274 회귀.",
                captured.getClass().getSimpleName(), actual));
    }

    /** 캡처한 객체에서 apiKey 를 꺼낸다. Input/Query 는 모두 record 이고, String 을 직접 받는 UseCase 도 있다. */
    private static String apiKeyOf(Object captured) {
        assertNotNull(captured, "UseCase 에 null 이 전달됐다");
        if (captured instanceof String apiKey) {
            return apiKey;
        }
        RecordComponent[] components = captured.getClass().getRecordComponents();
        assertNotNull(components,
                captured.getClass().getName() + " 는 record 가 아니다 — 테스트를 갱신할 것");
        for (RecordComponent component : components) {
            if ("apiKey".equals(component.getName())) {
                try {
                    return (String) component.getAccessor().invoke(captured);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("apiKey 접근자 호출 실패", e);
                }
            }
        }
        throw new AssertionError(captured.getClass().getSimpleName()
                + " 에 apiKey 컴포넌트가 없다 — 이 엔드포인트는 이 테스트 대상이 아니거나 테스트를 갱신해야 한다");
    }
}

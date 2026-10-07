package ai.univs.face.application.usecase;

import ai.univs.face.application.input.DeleteInput;
import ai.univs.face.application.input.ExtractInput;
import ai.univs.face.application.input.LivenessInput;
import ai.univs.face.application.input.RegisterByDescriptorInput;
import ai.univs.face.application.input.RegisterInput;
import ai.univs.face.application.input.UpdateInput;
import ai.univs.face.application.result.LivenessResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.application.service.FaceHistoryRecorder;
import ai.univs.face.application.service.SimilarityParser;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.domain.repository.FaceMatchRepository;
import ai.univs.face.infrastructure.feign.extract.ExtractFeign;
import ai.univs.face.infrastructure.feign.extract.dto.ExtractBodyFeignResponseDTO;
import ai.univs.face.infrastructure.feign.extract.dto.ExtractFeignResponseApi;
import ai.univs.face.infrastructure.feign.extract.dto.ExtractFeignResponseDTO;
import ai.univs.face.infrastructure.feign.extract.dto.LivenessBodyFeignResponseDTO;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.MatchFeignResponseDTO;
import ai.univs.face.infrastructure.feign.match.dto.VerifyFeignResponseDTO;
import ai.univs.face.infrastructure.repository.FaceLivenessJpaRepository;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceImageException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import ai.univs.face.shared.exception.TemporarilyUnavailableException;
import ai.univs.face.shared.exception.UpstreamCallException;
import ai.univs.face.shared.feign.dto.FeignResponseApi;
import ai.univs.face.shared.locale.MessageService;
import ai.univs.face.shared.web.enums.ErrorType;
import ai.univs.face.support.TestRecorders;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 쓰기·추출·라이브니스 유스케이스가 <b>같은 이력 계약</b>을 지키는가 (UG-358 2단계).
 *
 * <p>1단계 {@code ReadUseCaseHistoryContractTest} 와 같은 방식이다 — 저장하는 <b>순간</b>의 이력 상태를 적어 두고 마지막
 * 저장을 본다. 추출은 실제 {@link ExtractService} 로 돈다(fxp 만 목) — FACE_NOT_FOUND·TOO_MANY_FACES·라이브니스 -777 이
 * 운영과 같은 경로로 사유를 적는다.
 *
 * <p>예전(메서드 전체가 한 트랜잭션)과 비교한 최종 상태:
 * <ul>
 *   <li>성공 — 같다 (result=true, 사유 없음, 쓰기면 faceId)
 *   <li>추출 실패·match 오류 응답·MISMATCH — 같다 (사유와 함께 남는다). 단 추출(Extract)의 추출 실패는 예전에 행이
 *       롤백됐다(noRollbackFor 가 InvalidFaceImageException 만 적었다) — 이제 남는다. 의도된 수정이다.
 *   <li>5xx·타임아웃 — 예전에는 행이 사라졌다. 이제 INTERNAL_SERVER_ERROR 로 남는다
 *   <li>match 혼잡(UG-359) — 예전에는 행이 사라졌다. 이제 TEMPORARILY_UNAVAILABLE 로 남는다
 *   <li>결과 커밋 실패 — 예전에는 행이 사라졌다. 이제 「결과 미기록 + INTERNAL_SERVER_ERROR」로 남고, 등록이면 match 가
 *       준 faceId 가 남는다(UG-338 고아를 찾는 열쇠)
 * </ul>
 */
@DisplayName("UG-358: 쓰기·추출·라이브니스 유스케이스의 이력 계약")
class WriteUseCaseHistoryContractTest {

    private static final String CLIENT = "client-358";
    private static final String CALLER_ID = "0f8fad5b-d9cb-469f-a165-70867728950e";
    private static final String ISSUED_ID = "matcher-issued-358";
    private static final String EXISTING_ID = "existing-358";

    private MatchFeign matchFeign;
    private ExtractFeign extractFeign;
    private FaceLivenessJpaRepository livenesses;
    private FaceHistoryRepository histories;
    private ExtractService extractService;
    private SimilarityParser similarityParser;
    private FaceHistoryRecorder recorder;
    private final MockMultipartFile image = new MockMultipartFile("image", new byte[] {1});

    /** save 가 <b>성공한</b> 순간의 이력 상태. 같은 인스턴스를 캡처하면 테스트 끝의 메모리 값을 보게 된다. */
    private record Saved(boolean result, String failureMessage, String faceId) {
    }

    private final List<Saved> saved = new ArrayList<>();
    private final AtomicInteger historySaves = new AtomicInteger();
    /** n 번째 이력 저장을 실패시킨다 (0 이면 실패 없음). 1 = 시작, 2 = 결과 커밋(finish), 3 = 실패 기록. */
    private int failHistorySaveAt;
    private RuntimeException historySaveFailure;

    @BeforeEach
    void setUp() {
        matchFeign = mock(MatchFeign.class);
        extractFeign = mock(ExtractFeign.class);
        livenesses = mock(FaceLivenessJpaRepository.class);
        histories = mock(FaceHistoryRepository.class);
        FaceMatchRepository matches = mock(FaceMatchRepository.class);
        extractService = new ExtractService(extractFeign, mock(MessageService.class));
        similarityParser = new SimilarityParser();
        ReflectionTestUtils.setField(similarityParser, "FACE_MATCH_THRESHOLD", 0.85);
        recorder = TestRecorders.of(histories, matches, livenesses);

        saved.clear();
        historySaves.set(0);
        failHistorySaveAt = 0;
        given(histories.save(any())).willAnswer(i -> {
            if (historySaves.incrementAndGet() == failHistorySaveAt) throw historySaveFailure;
            FaceHistory h = i.getArgument(0);
            saved.add(new Saved(h.isResult(), h.getFailureMessage(), h.getFaceId()));
            return h;
        });
        fxp(1, 0);
    }

    // ---------------------------------------------------------------- 원격 응답

    private void fxp(int faceCount, int prediction) {
        given(extractFeign.extractWithOptionalLivenessAndMultiFace(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                .willReturn(new ExtractFeignResponseApi<>("SUCCESS", "ok", new ExtractFeignResponseDTO(
                        new ExtractBodyFeignResponseDTO("", "descriptor-358"),
                        new LivenessBodyFeignResponseDTO("0.99", prediction, prediction == 0 ? "real" : "spoof", "good", "0.5"),
                        faceCount)));
    }

    private static FeignResponseApi<MatchFeignResponseDTO> matchOk(String faceId) {
        return new FeignResponseApi<>(true, new MatchFeignResponseDTO("b", faceId), null);
    }

    private void verifyAnswers(String similarity) {
        given(matchFeign.verifyById(any())).willReturn(new FeignResponseApi<>(true, new VerifyFeignResponseDTO(similarity), null));
    }

    // ---------------------------------------------------------------- 유스케이스 표

    /**
     * 유스케이스 하나: 부르는 법, 성공 응답을 정하는 법, 마지막 원격 호출을 실패시키는 법, 성공 시 이력의 faceId.
     */
    private record Case(String name, Runnable execute, Runnable answerSuccess, Consumer<RuntimeException> failRemote,
                        String faceIdOnSuccess) {
        @Override
        public String toString() {
            return name;
        }
    }

    private List<Case> cases() {
        return List.of(
                new Case("Register(호출자 발급 id)",
                        () -> new RegisterUseCase(matchFeign, recorder, extractService)
                                .execute(new RegisterInput(CALLER_ID, image, "b", "t", CLIENT, true, true)),
                        () -> given(matchFeign.registerWithFaceId(any())).willReturn(matchOk(CALLER_ID)),
                        e -> given(matchFeign.registerWithFaceId(any())).willThrow(e),
                        CALLER_ID),
                new Case("Register(매처 발급 id)",
                        () -> new RegisterUseCase(matchFeign, recorder, extractService)
                                .execute(new RegisterInput("", image, "b", "t", CLIENT, true, true)),
                        () -> given(matchFeign.register(any())).willReturn(matchOk(ISSUED_ID)),
                        e -> given(matchFeign.register(any())).willThrow(e),
                        ISSUED_ID),
                new Case("RegisterByDescriptor",
                        () -> new RegisterByDescriptorUseCase(matchFeign, recorder)
                                .execute(new RegisterByDescriptorInput("b", "d", "t", CLIENT, null)),
                        () -> given(matchFeign.register(any())).willReturn(matchOk(ISSUED_ID)),
                        e -> given(matchFeign.register(any())).willThrow(e),
                        ISSUED_ID),
                new Case("Update",
                        () -> new UpdateUseCase(matchFeign, recorder, extractService, similarityParser)
                                .execute(new UpdateInput("b", EXISTING_ID, image, "t", CLIENT, true, true)),
                        () -> {
                            verifyAnswers("0.93");
                            given(matchFeign.update(any())).willReturn(matchOk(EXISTING_ID));
                        },
                        e -> {
                            verifyAnswers("0.93");
                            given(matchFeign.update(any())).willThrow(e);
                        },
                        EXISTING_ID),
                new Case("Delete",
                        () -> new DeleteUseCase(matchFeign, recorder).execute(new DeleteInput("b", EXISTING_ID, "t", CLIENT)),
                        () -> given(matchFeign.delete(any())).willReturn(matchOk(EXISTING_ID)),
                        e -> given(matchFeign.delete(any())).willThrow(e),
                        EXISTING_ID),
                new Case("Extract",
                        () -> new ExtractUseCase(recorder, extractService).execute(new ExtractInput(image, "t", CLIENT)),
                        () -> { },
                        e -> given(extractFeign.extractWithOptionalLivenessAndMultiFace(
                                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any())).willThrow(e),
                        ""),
                new Case("Liveness",
                        () -> new LivenessUseCase(recorder, extractService).execute(new LivenessInput(image, "t", CLIENT)),
                        () -> { },
                        e -> given(extractFeign.extractWithOptionalLivenessAndMultiFace(
                                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any())).willThrow(e),
                        ""));
    }

    static Stream<String> 전부() {
        return Stream.of("Register(호출자 발급 id)", "Register(매처 발급 id)", "RegisterByDescriptor", "Update", "Delete",
                "Extract", "Liveness");
    }

    static Stream<String> match_를_부르는_것() {
        return Stream.of("Register(호출자 발급 id)", "Register(매처 발급 id)", "RegisterByDescriptor", "Update", "Delete");
    }

    static Stream<String> 이미지를_추출하는_것() {
        return Stream.of("Register(호출자 발급 id)", "Register(매처 발급 id)", "Update", "Extract", "Liveness");
    }

    static Stream<String> 등록() {
        return Stream.of("Register(호출자 발급 id)", "Register(매처 발급 id)", "RegisterByDescriptor");
    }

    private Case caseNamed(String name) {
        return cases().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    private Saved lastSavedHistory() {
        assertThat(saved).as("이력 저장이 한 번도 없었다").isNotEmpty();
        return saved.getLast();
    }

    // ---------------------------------------------------------------- 계약

    @ParameterizedTest(name = "{0}")
    @MethodSource("전부")
    @DisplayName("성공 — 이력은 result=true, 사유 없음 (쓰기면 faceId)")
    void 성공(String name) {
        Case c = caseNamed(name);
        c.answerSuccess().run();

        c.execute().run();

        Saved history = lastSavedHistory();
        assertThat(history.result()).isTrue();
        assertThat(history.failureMessage()).isNull();
        assertThat(history.faceId()).isEqualTo(c.faceIdOnSuccess());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("전부")
    @DisplayName("하위 5xx·타임아웃 — 이력이 INTERNAL_SERVER_ERROR 로 남고 예외는 그대로 나간다 (예전에는 행이 롤백됐다)")
    void 하위_5xx(String name) {
        Case c = caseNamed(name);
        UpstreamCallException upstream = new UpstreamCallException(503, "Feign", "Service Unavailable");
        c.failRemote().accept(upstream);

        assertThatThrownBy(c.execute()::run).isSameAs(upstream);

        Saved history = lastSavedHistory();
        assertThat(history.result()).isFalse();
        assertThat(history.failureMessage()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR.name());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("match_를_부르는_것")
    @DisplayName("UG-359 match 혼잡 — 이력이 TEMPORARILY_UNAVAILABLE 로 남고 예외는 그대로 나간다 (503)")
    void match_혼잡(String name) {
        Case c = caseNamed(name);
        TemporarilyUnavailableException busy = new TemporarilyUnavailableException("MatchFeign");
        c.failRemote().accept(busy);

        assertThatThrownBy(c.execute()::run).isSameAs(busy);

        Saved history = lastSavedHistory();
        assertThat(history.result()).isFalse();
        assertThat(history.failureMessage()).isEqualTo(ErrorType.TEMPORARILY_UNAVAILABLE.name());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("match_를_부르는_것")
    @DisplayName("match 오류 응답 — InvalidFaceModuleException 으로 바뀌고 그 유형이 사유로 남는다 (예전과 같다)")
    void match_오류_응답(String name) {
        Case c = caseNamed(name);
        c.failRemote().accept(new CustomFeignException("MATCH-004", "INVALID_FACE_ID", "없음"));

        assertThatThrownBy(c.execute()::run)
                .isInstanceOf(InvalidFaceModuleException.class)
                .extracting(e -> ((InvalidFaceModuleException) e).getType())
                .isEqualTo("INVALID_FACE_ID");

        Saved history = lastSavedHistory();
        assertThat(history.result()).isFalse();
        assertThat(history.failureMessage()).isEqualTo("INVALID_FACE_ID");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("이미지를_추출하는_것")
    @DisplayName("얼굴 없음(FACE_NOT_FOUND) — 사유와 함께 남는다 (Extract 는 예전에 행이 롤백됐다 — 의도된 수정)")
    void 얼굴_없음(String name) {
        Case c = caseNamed(name);
        fxp(0, 0);

        assertThatThrownBy(c.execute()::run)
                .isInstanceOf(InvalidFaceModuleException.class)
                .extracting(e -> ((InvalidFaceModuleException) e).getType())
                .isEqualTo("FACE_NOT_FOUND");

        Saved history = lastSavedHistory();
        assertThat(history.result()).isFalse();
        assertThat(history.failureMessage()).isEqualTo("FACE_NOT_FOUND");
        verify(matchFeign, never()).register(any());
        verify(matchFeign, never()).registerWithFaceId(any());
        verify(matchFeign, never()).update(any());
    }

    /** 추출(Extract)은 다중 얼굴·라이브니스를 검사하지 않는다. */
    static Stream<String> 다중_얼굴과_라이브니스를_검사하는_것() {
        return Stream.of("Register(호출자 발급 id)", "Register(매처 발급 id)", "Update", "Liveness");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("다중_얼굴과_라이브니스를_검사하는_것")
    @DisplayName("얼굴 여럿·라이브니스 실패 — 등록·수정은 예외와 사유, 라이브니스는 예외 없이 사유 + result=true (예전과 같다)")
    void 얼굴_여럿과_라이브니스_실패(String name) {
        Case c = caseNamed(name);

        fxp(2, 0);
        if (name.equals("Liveness")) {
            c.execute().run();
            assertThat(lastSavedHistory()).isEqualTo(new Saved(true, "TOO_MANY_FACES", ""));
        } else {
            assertThatThrownBy(c.execute()::run)
                    .isInstanceOf(InvalidFaceModuleException.class)
                    .extracting(e -> ((InvalidFaceModuleException) e).getType())
                    .isEqualTo("TOO_MANY_FACES");
            assertThat(lastSavedHistory().failureMessage()).isEqualTo("TOO_MANY_FACES");
            assertThat(lastSavedHistory().result()).isFalse();
        }

        saved.clear();
        fxp(1, -777);
        if (name.equals("Liveness")) {
            LivenessResult result = new LivenessUseCase(recorder, extractService)
                    .execute(new LivenessInput(image, "t", CLIENT));
            assertThat(result.success()).isFalse();
            assertThat(lastSavedHistory()).isEqualTo(new Saved(true, "SPOOF", ""));
        } else {
            assertThatThrownBy(c.execute()::run)
                    .isInstanceOf(InvalidFaceModuleException.class)
                    .extracting(e -> ((InvalidFaceModuleException) e).getCode())
                    .isEqualTo("-777");
            assertThat(lastSavedHistory().failureMessage()).isEqualTo("SPOOF");
            assertThat(lastSavedHistory().result()).isFalse();
        }
        verify(livenesses, atLeastOnce()).save(any());   // 라이브니스 판정 행은 실패여도 남는다(예전과 같다)
    }

    @Test
    @DisplayName("Update MISMATCH — InvalidFaceImageException(MISMATCH) 로 끝나고 사유가 남는다, match 변경은 부르지 않는다")
    void 수정_불일치() {
        verifyAnswers("0.30");

        assertThatThrownBy(() -> new UpdateUseCase(matchFeign, recorder, extractService, similarityParser)
                .execute(new UpdateInput("b", EXISTING_ID, image, "t", CLIENT, true, true)))
                .isInstanceOf(InvalidFaceImageException.class)
                .extracting(e -> ((InvalidFaceImageException) e).getErrorType())
                .isEqualTo(ErrorType.MISMATCH);

        assertThat(lastSavedHistory()).isEqualTo(new Saved(false, "MISMATCH", EXISTING_ID));
        verify(matchFeign, never()).update(any());
    }

    /**
     * UG-338: match 등록은 성공했는데 결과 커밋이 실패했다. 클라이언트는 오류를 받고 match 에는 특징점이 남는다. 실패
     * 이력에 그 faceId 가 남아야 운영자가 고아를 찾는다 — 매처 발급 id 는 시작 행에 없으므로 특히 그렇다.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("등록")
    @DisplayName("UG-338 등록 뒤 결과 커밋 실패 — 결과 미기록 + INTERNAL_SERVER_ERROR 에 match 가 준 faceId 가 남는다")
    void 등록_뒤_커밋_실패(String name) {
        Case c = caseNamed(name);
        c.answerSuccess().run();
        DataAccessResourceFailureException dbDown = new DataAccessResourceFailureException("db down");
        failHistorySaveAt = 2;
        historySaveFailure = dbDown;

        assertThatThrownBy(c.execute()::run).isSameAs(dbDown);

        assertThat(saved).hasSize(2);   // 시작, 실패 기록
        assertThat(lastSavedHistory()).isEqualTo(
                new Saved(false, ErrorType.INTERNAL_SERVER_ERROR.name(), c.faceIdOnSuccess()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("전부")
    @DisplayName("결과 커밋 실패 — 「결과 미기록 + INTERNAL_SERVER_ERROR」로 남는다 (예전에는 행이 롤백됐다)")
    void 결과_커밋_실패(String name) {
        Case c = caseNamed(name);
        c.answerSuccess().run();
        DataAccessResourceFailureException dbDown = new DataAccessResourceFailureException("db down");
        failHistorySaveAt = 2;
        historySaveFailure = dbDown;

        assertThatThrownBy(c.execute()::run).isSameAs(dbDown);

        Saved history = lastSavedHistory();
        assertThat(history.result()).isFalse();
        assertThat(history.failureMessage()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR.name());
    }

    /**
     * UG-359: 결과 커밋이 우리 풀 고갈로 실패하면 같은 풀로 또 저장하지 않는다(503 이 늦어진다). 행은 시작 상태로 남는다 —
     * 등록이면 faceId 는 {@code FaceHistoryRecorder.finish} 의 로그에만 남는다. 알려진 한계로 못 박아 둔다.
     */
    @Test
    @DisplayName("UG-359 등록 뒤 결과 커밋이 풀 고갈 — 다시 저장하지 않고 원래 예외(→ 503)가 나간다")
    void 등록_뒤_풀_고갈() {
        Case c = caseNamed("Register(매처 발급 id)");
        c.answerSuccess().run();
        CannotCreateTransactionException poolTimeout = new CannotCreateTransactionException("no connection",
                new SQLTransientConnectionException("HikariPool-1 - Connection is not available"));
        failHistorySaveAt = 2;
        historySaveFailure = poolTimeout;

        assertThatThrownBy(c.execute()::run).isSameAs(poolTimeout);

        assertThat(historySaves.get()).as("시작, 실패한 결과 커밋 — 실패 기록은 시도하지 않는다").isEqualTo(2);
        assertThat(saved).containsExactly(new Saved(false, null, ""));
    }
}

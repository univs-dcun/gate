package ai.univs.face.application.usecase;

import ai.univs.face.application.input.IdentifyByDescriptorInput;
import ai.univs.face.application.input.IdentifyCandidatesByDescriptorInput;
import ai.univs.face.application.input.IdentifyInput;
import ai.univs.face.application.input.VerifyByDescriptorInput;
import ai.univs.face.application.input.VerifyByIdInput;
import ai.univs.face.application.input.VerifyByImageInput;
import ai.univs.face.application.result.ExtractResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.application.service.FaceHistoryRecorder;
import ai.univs.face.application.service.SimilarityParser;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.FaceMatch;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.domain.repository.FaceMatchRepository;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.IdentifyCandidatesFeignResponseDTO;
import ai.univs.face.infrastructure.feign.match.dto.IdentifyFeignResponseDTO;
import ai.univs.face.infrastructure.feign.match.dto.VerifyFeignResponseDTO;
import ai.univs.face.shared.exception.UpstreamCallException;
import ai.univs.face.shared.feign.dto.FeignResponseApi;
import ai.univs.face.support.TestRecorders;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 읽기 유스케이스 6개가 <b>같은 이력 계약</b>을 지키는가 (UG-358, 반박 리뷰 M1).
 *
 * <p>트랜잭션을 나누면서 저장이 분기마다 흩어졌다. 그중 하나를 빠뜨려도 각 유스케이스 단위 테스트는 대부분 초록이었다
 * (반박 리뷰 변이: 실패 기록 제거 5/6, 미달 분기 결과 커밋 제거 4/6 생존). 여기서는 여섯 개를 같은 표로 돌린다.
 * <ul>
 *   <li>match 가 5xx — 마지막으로 저장된 이력의 사유가 INTERNAL_SERVER_ERROR 다 (예전에는 행이 롤백으로 사라졌다)
 *   <li>임계치 미달 — 결과 행이 저장되고, 마지막 이력은 NOT_MATCH 다
 *   <li>통과 — 결과 행이 저장되고, 마지막 이력은 result=true 에 사유가 없다
 * </ul>
 *
 * <p><b>UG-363: 이력은 요청 하나에 한 번 쓴다.</b> 시작 행을 먼저 커밋하지 않으므로 위 세 경우 모두 이력 저장이 정확히
 * 한 번이다(통과·미달은 결과 행과 한 트랜잭션, 실패는 실패 기록). 우리 쪽 예외·결과 커밋 실패도 행이 남고(UG-280),
 * DB 응답 시간 초과면 다시 저장하지 않는다(UG-367).
 */
@DisplayName("UG-358: 읽기 유스케이스의 이력 계약")
class ReadUseCaseHistoryContractTest {

    private static final String CLIENT = "client-358";

    private MatchFeign matchFeign;
    private FaceHistoryRepository histories;
    private FaceMatchRepository matches;
    private ExtractService extractService;
    private SimilarityParser similarityParser;
    private FaceHistoryRecorder recorder;
    private final List<Saved> saved = new java.util.ArrayList<>();

    /** save 가 불린 순간의 이력 상태. */
    private record Saved(boolean result, String failureMessage) {
    }

    @BeforeEach
    void setUp() {
        matchFeign = mock(MatchFeign.class);
        histories = mock(FaceHistoryRepository.class);
        matches = mock(FaceMatchRepository.class);
        extractService = mock(ExtractService.class);
        similarityParser = new SimilarityParser();
        ReflectionTestUtils.setField(similarityParser, "FACE_MATCH_THRESHOLD", 0.85);
        recorder = TestRecorders.of(histories, matches);

        // 저장하는 <b>순간</b>의 상태를 적어 둔다. 같은 인스턴스를 캡처하면 테스트 끝의 메모리 값을 보게 되어, 상태를 바꾸기 전에
        // 커밋하는 순서 실수(finish 를 fail·successMatch 보다 먼저)가 초록으로 남는다(2차 반박 리뷰 L).
        saved.clear();
        given(histories.save(any())).willAnswer(i -> {
            FaceHistory h = i.getArgument(0);
            saved.add(new Saved(h.isResult(), h.getFailureMessage()));
            return h;
        });
        given(matches.save(any())).willAnswer(i -> i.getArgument(0));
        given(extractService.extract(any(), any(), any(), anyBoolean(), anyBoolean()))
                .willReturn(new ExtractResult("descriptor-358"));
    }

    /** 유스케이스 하나: 만들고 부르는 법, 그리고 match 응답을 정하는 법. */
    private record Case(String name, Runnable execute, Consumer<String> answerSimilarity,
                        Consumer<RuntimeException> failWith) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static final UpstreamCallException MATCH_503 =
            new UpstreamCallException(503, "MatchFeign", "Service Unavailable");

    private List<Case> cases() {
        MockMultipartFile image = new MockMultipartFile("image", new byte[] {1});
        return List.of(
                new Case("Identify",
                        () -> new IdentifyUseCase(matchFeign, recorder, extractService, similarityParser)
                                .execute(new IdentifyInput("b", image, "t", CLIENT, false, false)),
                        sim -> given(matchFeign.identify(any())).willReturn(
                                new FeignResponseApi<>(true, new IdentifyFeignResponseDTO("face-1", sim), null)),
                        e -> given(matchFeign.identify(any())).willThrow(e)),
                new Case("IdentifyByDescriptor",
                        () -> new IdentifyByDescriptorUseCase(matchFeign, recorder, similarityParser)
                                .execute(new IdentifyByDescriptorInput("b", "d", "t", CLIENT)),
                        sim -> given(matchFeign.identify(any())).willReturn(
                                new FeignResponseApi<>(true, new IdentifyFeignResponseDTO("face-1", sim), null)),
                        e -> given(matchFeign.identify(any())).willThrow(e)),
                new Case("IdentifyCandidatesByDescriptor",
                        () -> new IdentifyCandidatesByDescriptorUseCase(matchFeign, recorder, similarityParser)
                                .execute(new IdentifyCandidatesByDescriptorInput("b", "d", 0.85, 3, "t", CLIENT)),
                        sim -> given(matchFeign.identifyCandidates(any())).willReturn(new FeignResponseApi<>(true,
                                new IdentifyCandidatesFeignResponseDTO(List.of(
                                        new IdentifyCandidatesFeignResponseDTO.Candidate("face-1", sim))), null)),
                        e -> given(matchFeign.identifyCandidates(any())).willThrow(e)),
                new Case("VerifyById",
                        () -> new VerifyByIdUseCase(matchFeign, recorder, extractService, similarityParser)
                                .execute(new VerifyByIdInput("b", "face-1", image, "t", CLIENT, false, false)),
                        sim -> given(matchFeign.verifyById(any())).willReturn(
                                new FeignResponseApi<>(true, new VerifyFeignResponseDTO(sim), null)),
                        e -> given(matchFeign.verifyById(any())).willThrow(e)),
                new Case("VerifyByImage",
                        () -> new VerifyByImageUseCase(matchFeign, recorder, extractService, similarityParser)
                                .execute(new VerifyByImageInput(image, image, "t", CLIENT, false, false)),
                        sim -> given(matchFeign.verifyByDescriptor(any())).willReturn(
                                new FeignResponseApi<>(true, new VerifyFeignResponseDTO(sim), null)),
                        e -> given(matchFeign.verifyByDescriptor(any())).willThrow(e)),
                new Case("VerifyByDescriptor",
                        () -> new VerifyByDescriptorUseCase(matchFeign, recorder, similarityParser)
                                .execute(new VerifyByDescriptorInput("d1", "d2", "t", CLIENT)),
                        sim -> given(matchFeign.verifyByDescriptor(any())).willReturn(
                                new FeignResponseApi<>(true, new VerifyFeignResponseDTO(sim), null)),
                        e -> given(matchFeign.verifyByDescriptor(any())).willThrow(e)));
    }

    static Stream<Arguments> names() {
        return Stream.of("Identify", "IdentifyByDescriptor", "IdentifyCandidatesByDescriptor",
                "VerifyById", "VerifyByImage", "VerifyByDescriptor").map(Arguments::of);
    }

    private Case caseNamed(String name) {
        return cases().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    private Saved lastSavedHistory() {
        assertThat(saved).as("이력 저장이 한 번도 없었다").isNotEmpty();
        return saved.getLast();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    @DisplayName("match 5xx — 이력이 INTERNAL_SERVER_ERROR 로 남고 예외는 그대로 나간다")
    void match_5xx(String name) {
        Case c = caseNamed(name);
        c.failWith().accept(MATCH_503);

        assertThatThrownBy(c.execute()::run).isSameAs(MATCH_503);

        assertThat(saved).as("UG-363: 실패 기록 한 번 — 시작 행이 없다").hasSize(1);
        Saved history = lastSavedHistory();
        assertThat(history.result()).isFalse();
        assertThat(history.failureMessage()).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    @DisplayName("임계치 미달 — 결과 행이 저장되고 이력은 NOT_MATCH")
    void 미달(String name) {
        Case c = caseNamed(name);
        c.answerSimilarity().accept("0.30");

        c.execute().run();

        verify(matches).save(any(FaceMatch.class));
        assertThat(saved).as("UG-363: 결과 커밋 한 번 — 시작 행이 없다").hasSize(1);
        Saved history = lastSavedHistory();
        assertThat(history.result()).isFalse();
        assertThat(history.failureMessage()).isEqualTo("NOT_MATCH");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    @DisplayName("통과 — 결과 행이 저장되고 이력은 result=true, 사유 없음")
    void 통과(String name) {
        Case c = caseNamed(name);
        c.answerSimilarity().accept("0.93");

        c.execute().run();

        verify(matches).save(any(FaceMatch.class));
        assertThat(saved).as("UG-363: 결과 커밋 한 번 — 시작 행이 없다").hasSize(1);
        Saved history = lastSavedHistory();
        assertThat(history.result()).isTrue();
        assertThat(history.failureMessage()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    @DisplayName("UG-363 우리 쪽 예외(IllegalStateException 등) — 시작 행이 없어도 실패 기록이 INTERNAL_SERVER_ERROR 로 한 번 남긴다 (UG-280)")
    void 우리_쪽_예외(String name) {
        Case c = caseNamed(name);
        IllegalStateException bug = new IllegalStateException("bug");
        c.failWith().accept(bug);

        assertThatThrownBy(c.execute()::run).isSameAs(bug);

        assertThat(saved).containsExactly(new Saved(false, "INTERNAL_SERVER_ERROR"));
        verify(matches, never()).save(any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    @DisplayName("UG-363 결과 커밋 실패(DB 장애) — 실패 기록이 「결과 미기록 + INTERNAL_SERVER_ERROR」로 다시 INSERT 한다")
    void 결과_커밋_실패(String name) {
        Case c = caseNamed(name);
        c.answerSimilarity().accept("0.93");
        DataAccessResourceFailureException dbDown = new DataAccessResourceFailureException("db down");
        willThrowOnFirstHistorySave(dbDown);

        assertThatThrownBy(c.execute()::run).isSameAs(dbDown);

        assertThat(historySaveCalls).as("결과 커밋(실패), 실패 기록").hasSize(2);
        assertThat(saved).containsExactly(new Saved(false, "INTERNAL_SERVER_ERROR"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    @DisplayName("UG-363·UG-367 결과 커밋이 DB 응답 시간 초과 — 멈춘 DB 에 다시 저장하지 않고 원래 예외(→ 503)가 나간다")
    void 결과_커밋이_DB_응답_시간_초과(String name) {
        Case c = caseNamed(name);
        c.answerSimilarity().accept("0.93");
        DataAccessResourceFailureException readTimeout = new DataAccessResourceFailureException("could not execute statement",
                new SQLException("An I/O error occurred while sending to the backend.", "08006",
                        new SocketTimeoutException("Read timed out")));
        willThrowOnFirstHistorySave(readTimeout);

        assertThatThrownBy(c.execute()::run).isSameAs(readTimeout);

        assertThat(historySaveCalls).as("결과 커밋 한 번 — 실패 기록은 시도하지 않는다").hasSize(1);
        assertThat(saved).isEmpty();
    }

    /** save 호출(성공·실패 모두)의 순번. */
    private final List<Integer> historySaveCalls = new java.util.ArrayList<>();

    /** 첫 이력 저장(결과 커밋)만 실패시킨다. */
    private void willThrowOnFirstHistorySave(RuntimeException failure) {
        historySaveCalls.clear();
        // setUp 의 스텁을 덮는다 — given(save(any())) 로 다시 정하면 그 스텁이 null 인자로 한 번 불린다
        willAnswer(i -> {
            historySaveCalls.add(historySaveCalls.size() + 1);
            if (historySaveCalls.size() == 1) throw failure;
            FaceHistory h = i.getArgument(0);
            saved.add(new Saved(h.isResult(), h.getFailureMessage()));
            return h;
        }).given(histories).save(any());
    }
}

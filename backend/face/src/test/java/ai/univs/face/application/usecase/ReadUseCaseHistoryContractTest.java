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
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
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

    @BeforeEach
    void setUp() {
        matchFeign = mock(MatchFeign.class);
        histories = mock(FaceHistoryRepository.class);
        matches = mock(FaceMatchRepository.class);
        extractService = mock(ExtractService.class);
        similarityParser = new SimilarityParser();
        ReflectionTestUtils.setField(similarityParser, "FACE_MATCH_THRESHOLD", 0.85);
        recorder = TestRecorders.of(histories, matches);

        given(histories.save(any())).willAnswer(i -> i.getArgument(0));
        given(matches.save(any())).willAnswer(i -> i.getArgument(0));
        given(extractService.extract(any(), any(), any(), anyBoolean(), anyBoolean()))
                .willReturn(new ExtractResult("descriptor-358"));
    }

    /** 유스케이스 하나: 만들고 부르는 법, 그리고 match 응답을 정하는 법. */
    private record Case(String name, Runnable execute, Consumer<String> answerSimilarity, Runnable failWith5xx) {
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
                        () -> given(matchFeign.identify(any())).willThrow(MATCH_503)),
                new Case("IdentifyByDescriptor",
                        () -> new IdentifyByDescriptorUseCase(matchFeign, recorder, similarityParser)
                                .execute(new IdentifyByDescriptorInput("b", "d", "t", CLIENT)),
                        sim -> given(matchFeign.identify(any())).willReturn(
                                new FeignResponseApi<>(true, new IdentifyFeignResponseDTO("face-1", sim), null)),
                        () -> given(matchFeign.identify(any())).willThrow(MATCH_503)),
                new Case("IdentifyCandidatesByDescriptor",
                        () -> new IdentifyCandidatesByDescriptorUseCase(matchFeign, recorder, similarityParser)
                                .execute(new IdentifyCandidatesByDescriptorInput("b", "d", 0.85, 3, "t", CLIENT)),
                        sim -> given(matchFeign.identifyCandidates(any())).willReturn(new FeignResponseApi<>(true,
                                new IdentifyCandidatesFeignResponseDTO(List.of(
                                        new IdentifyCandidatesFeignResponseDTO.Candidate("face-1", sim))), null)),
                        () -> given(matchFeign.identifyCandidates(any())).willThrow(MATCH_503)),
                new Case("VerifyById",
                        () -> new VerifyByIdUseCase(matchFeign, recorder, extractService, similarityParser)
                                .execute(new VerifyByIdInput("b", "face-1", image, "t", CLIENT, false, false)),
                        sim -> given(matchFeign.verifyById(any())).willReturn(
                                new FeignResponseApi<>(true, new VerifyFeignResponseDTO(sim), null)),
                        () -> given(matchFeign.verifyById(any())).willThrow(MATCH_503)),
                new Case("VerifyByImage",
                        () -> new VerifyByImageUseCase(matchFeign, recorder, extractService, similarityParser)
                                .execute(new VerifyByImageInput(image, image, "t", CLIENT, false, false)),
                        sim -> given(matchFeign.verifyByDescriptor(any())).willReturn(
                                new FeignResponseApi<>(true, new VerifyFeignResponseDTO(sim), null)),
                        () -> given(matchFeign.verifyByDescriptor(any())).willThrow(MATCH_503)),
                new Case("VerifyByDescriptor",
                        () -> new VerifyByDescriptorUseCase(matchFeign, recorder, similarityParser)
                                .execute(new VerifyByDescriptorInput("d1", "d2", "t", CLIENT)),
                        sim -> given(matchFeign.verifyByDescriptor(any())).willReturn(
                                new FeignResponseApi<>(true, new VerifyFeignResponseDTO(sim), null)),
                        () -> given(matchFeign.verifyByDescriptor(any())).willThrow(MATCH_503)));
    }

    static Stream<Arguments> names() {
        return Stream.of("Identify", "IdentifyByDescriptor", "IdentifyCandidatesByDescriptor",
                "VerifyById", "VerifyByImage", "VerifyByDescriptor").map(Arguments::of);
    }

    private Case caseNamed(String name) {
        return cases().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    private FaceHistory lastSavedHistory() {
        ArgumentCaptor<FaceHistory> captor = ArgumentCaptor.forClass(FaceHistory.class);
        verify(histories, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    @DisplayName("match 5xx — 이력이 INTERNAL_SERVER_ERROR 로 남고 예외는 그대로 나간다")
    void match_5xx(String name) {
        Case c = caseNamed(name);
        c.failWith5xx().run();

        assertThatThrownBy(c.execute()::run).isSameAs(MATCH_503);

        FaceHistory history = lastSavedHistory();
        assertThat(history.isResult()).isFalse();
        assertThat(history.getFailureMessage()).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    @DisplayName("임계치 미달 — 결과 행이 저장되고 이력은 NOT_MATCH")
    void 미달(String name) {
        Case c = caseNamed(name);
        c.answerSimilarity().accept("0.30");

        c.execute().run();

        verify(matches).save(any(FaceMatch.class));
        FaceHistory history = lastSavedHistory();
        assertThat(history.isResult()).isFalse();
        assertThat(history.getFailureMessage()).isEqualTo("NOT_MATCH");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    @DisplayName("통과 — 결과 행이 저장되고 이력은 result=true, 사유 없음")
    void 통과(String name) {
        Case c = caseNamed(name);
        c.answerSimilarity().accept("0.93");

        c.execute().run();

        verify(matches).save(any(FaceMatch.class));
        FaceHistory history = lastSavedHistory();
        assertThat(history.isResult()).isTrue();
        assertThat(history.getFailureMessage()).isNull();
    }
}

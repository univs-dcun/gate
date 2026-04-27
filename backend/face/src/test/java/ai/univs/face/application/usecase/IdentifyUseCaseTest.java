package ai.univs.face.application.usecase;

import ai.univs.face.application.input.IdentifyInput;
import ai.univs.face.application.result.ExtractResult;
import ai.univs.face.application.result.IdentifyResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.application.service.SimilarityParser;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.FaceMatch;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.domain.repository.FaceMatchRepository;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.IdentifyFeignResponseDTO;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import ai.univs.face.shared.feign.dto.FeignResponseApi;
import ai.univs.face.shared.web.enums.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IdentifyUseCaseTest {

    @Mock private MatchFeign matchFeign;
    @Mock private FaceHistoryRepository faceHistoryRepository;
    @Mock private FaceMatchRepository faceMatchRepository;
    @Mock private ExtractService extractService;
    @Mock private SimilarityParser similarityParser;
    @Mock private MultipartFile faceImage;

    @InjectMocks
    private IdentifyUseCase identifyUseCase;

    private static final String BRANCH  = "branch-A";
    private static final String FACE_ID = "face-001";
    private static final String TXN     = "txn-001";
    private static final String CLIENT  = "client-A";

    private IdentifyInput input;

    @BeforeEach
    void setUp() {
        input = new IdentifyInput(BRANCH, faceImage, TXN, CLIENT, true, true);
        given(extractService.extract(any(), any(), any(), anyBoolean(), anyBoolean()))
                .willReturn(new ExtractResult("descriptor-xyz"));
        lenient().when(similarityParser.getThreshold()).thenReturn(0.85);
        lenient().when(similarityParser.getThresholdString()).thenReturn("0.85");
    }

    private FeignResponseApi<IdentifyFeignResponseDTO> identifyResponse(String faceId, String similarity) {
        return new FeignResponseApi<>(true, new IdentifyFeignResponseDTO(faceId, similarity), null);
    }

    @Nested
    @DisplayName("유사도 임계치 기반 결과 분기")
    class SimilarityBranching {

        @Test
        @DisplayName("유사도 >= 임계치 → faceId 포함 result=true 반환, faceMatch.updateFaceId() 호출")
        void execute_whenIdentifySucceeds_returnsFaceId() {
            given(matchFeign.identify(any())).willReturn(identifyResponse(FACE_ID, "0.93"));
            given(similarityParser.parseDoubleSimilarity("0.93")).willReturn(0.93);
            given(similarityParser.isMatchingBySimilarity(0.93)).willReturn(true);
            given(faceMatchRepository.save(any())).willAnswer(i -> i.getArgument(0));

            IdentifyResult result = identifyUseCase.execute(input);

            assertThat(result.result()).isTrue();
            assertThat(result.faceId()).isEqualTo(FACE_ID);
            assertThat(result.similarity()).isEqualTo("0.93");

            ArgumentCaptor<FaceMatch> matchCaptor = ArgumentCaptor.forClass(FaceMatch.class);
            verify(faceMatchRepository).save(matchCaptor.capture());
            assertThat(matchCaptor.getValue().getFaceId()).isEqualTo(FACE_ID);
        }

        @Test
        @DisplayName("유사도 < 임계치 → faceId 빈값, result=false 반환, history.fail(NOT_MATCH) 호출")
        void execute_whenSimilarityBelowThreshold_returnsEmptyFaceId() {
            given(matchFeign.identify(any())).willReturn(identifyResponse("", "0.30"));
            given(similarityParser.parseDoubleSimilarity("0.30")).willReturn(0.30);
            given(similarityParser.isMatchingBySimilarity(0.30)).willReturn(false);

            ArgumentCaptor<FaceHistory> captor = ArgumentCaptor.forClass(FaceHistory.class);

            IdentifyResult result = identifyUseCase.execute(input);

            assertThat(result.result()).isFalse();
            assertThat(result.faceId()).isEmpty();
            verify(faceHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getFailureMessage()).isEqualTo("NOT_MATCH");
        }
    }

    @Nested
    @DisplayName("예외 처리")
    class ExceptionHandling {

        @Test
        @DisplayName("MatchFeign 실패 → InvalidFaceModuleException 변환 및 history.fail() 호출")
        void execute_whenMatchFeignFails_throwsInvalidFaceModuleException() {
            given(matchFeign.identify(any()))
                    .willThrow(new CustomFeignException("E007", "SERVER_ERROR", "매처 서버 오류"));

            ArgumentCaptor<FaceHistory> captor = ArgumentCaptor.forClass(FaceHistory.class);

            assertThatThrownBy(() -> identifyUseCase.execute(input))
                    .isInstanceOf(InvalidFaceModuleException.class)
                    .extracting(e -> ((InvalidFaceModuleException) e).getType())
                    .isEqualTo("SERVER_ERROR");

            verify(faceHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getFailureMessage()).isEqualTo("SERVER_ERROR");
        }

        @Test
        @DisplayName("ExtractService 실패 시 예외가 그대로 전파된다")
        void execute_whenExtractFails_exceptionPropagates() {
            ErrorType errorType = ErrorType.FACE_NOT_FOUND;

            given(extractService.extract(any(), any(), any(), anyBoolean(), anyBoolean()))
                    .willThrow(new InvalidFaceModuleException(errorType.getCode(), errorType.name(), "얼굴 없음"));

            assertThatThrownBy(() -> identifyUseCase.execute(input))
                    .isInstanceOf(InvalidFaceModuleException.class);
        }
    }
}

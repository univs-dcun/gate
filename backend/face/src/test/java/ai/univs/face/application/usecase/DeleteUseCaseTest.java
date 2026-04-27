package ai.univs.face.application.usecase;

import ai.univs.face.application.input.DeleteInput;
import ai.univs.face.application.result.DeleteResult;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.MatchFeignResponseDTO;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import ai.univs.face.shared.feign.dto.FeignResponseApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeleteUseCaseTest {

    @Mock private MatchFeign matchFeign;
    @Mock private FaceHistoryRepository faceHistoryRepository;

    @InjectMocks
    private DeleteUseCase deleteUseCase;

    private static final String BRANCH  = "branch-A";
    private static final String FACE_ID = "face-001";
    private static final String TXN     = "txn-001";
    private static final String CLIENT  = "client-A";

    private DeleteInput input;

    @BeforeEach
    void setUp() {
        input = new DeleteInput(BRANCH, FACE_ID, TXN, CLIENT);
    }

    @Nested
    @DisplayName("정상 처리")
    class Success {

        @Test
        @DisplayName("MatchFeign 삭제 성공 → DeleteResult 반환 및 이력 저장")
        void execute_success_returnsDeleteResult() {
            given(matchFeign.delete(any()))
                    .willReturn(new FeignResponseApi<>(true, new MatchFeignResponseDTO(BRANCH, FACE_ID), null));

            DeleteResult result = deleteUseCase.execute(input);

            assertThat(result.faceId()).isEqualTo(FACE_ID);
            assertThat(result.branchName()).isEqualTo(BRANCH);
            assertThat(result.transactionUuid()).isEqualTo(TXN);
            verify(faceHistoryRepository).save(any(FaceHistory.class));
        }
    }

    @Nested
    @DisplayName("예외 처리")
    class ExceptionHandling {

        @Test
        @DisplayName("MatchFeign 실패 → InvalidFaceModuleException 변환 및 history.fail() 호출")
        void execute_whenMatchFeignFails_throwsInvalidFaceModuleException() {
            given(matchFeign.delete(any()))
                    .willThrow(new CustomFeignException("E003", "NOT_FOUND", "등록되지 않은 얼굴"));

            ArgumentCaptor<FaceHistory> captor = ArgumentCaptor.forClass(FaceHistory.class);

            assertThatThrownBy(() -> deleteUseCase.execute(input))
                    .isInstanceOf(InvalidFaceModuleException.class)
                    .extracting(e -> ((InvalidFaceModuleException) e).getType())
                    .isEqualTo("NOT_FOUND");

            verify(faceHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getFailureMessage()).isEqualTo("NOT_FOUND");
        }
    }
}

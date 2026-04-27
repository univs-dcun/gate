package ai.univs.match.application.usecase;

import ai.univs.match.application.result.MatchResult;
import ai.univs.match.application.service.RegisterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@DisplayName("RegisterUseCase")
@ExtendWith(MockitoExtension.class)
class RegisterUseCaseTest {

    @Mock
    private RegisterService registerService;

    @InjectMocks
    private RegisterUseCase registerUseCase;

    private static final String BRANCH_NAME = "testBranch";
    private static final String DESCRIPTOR = "descriptor-value";

    @Test
    @DisplayName("생성된 faceId를 MatchResult에 담아 반환한다")
    void whenExecuted_thenReturnsMatchResultWithGeneratedFaceId() {
        MatchResult result = registerUseCase.execute(BRANCH_NAME, DESCRIPTOR);

        assertThat(result.faceId()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("반환된 faceId는 UUID 형식이다")
    void whenExecuted_thenReturnedFaceIdIsUuidFormat() {
        MatchResult result = registerUseCase.execute(BRANCH_NAME, DESCRIPTOR);

        assertThat(result.faceId()).satisfies(id -> UUID.fromString(id));
    }

    @Test
    @DisplayName("입력받은 branchName을 MatchResult에 담아 반환한다")
    void whenExecuted_thenReturnsMatchResultWithBranchName() {
        MatchResult result = registerUseCase.execute(BRANCH_NAME, DESCRIPTOR);

        assertThat(result.branchName()).isEqualTo(BRANCH_NAME);
    }

    @RepeatedTest(3)
    @DisplayName("호출마다 서로 다른 faceId를 생성한다")
    void whenExecutedMultipleTimes_thenGeneratesDifferentFaceIds() {
        MatchResult result1 = registerUseCase.execute(BRANCH_NAME, DESCRIPTOR);
        MatchResult result2 = registerUseCase.execute(BRANCH_NAME, DESCRIPTOR);

        assertThat(result1.faceId()).isNotEqualTo(result2.faceId());
    }

    @Test
    @DisplayName("branchName, 생성된 faceId, descriptor를 RegisterService에 전달하고 동일한 faceId를 반환한다")
    void whenExecuted_thenDelegatesCorrectArgsToRegisterServiceAndReturnsSameFaceId() {
        ArgumentCaptor<String> faceIdCaptor = ArgumentCaptor.forClass(String.class);

        MatchResult result = registerUseCase.execute(BRANCH_NAME, DESCRIPTOR);

        verify(registerService).register(eq(BRANCH_NAME), faceIdCaptor.capture(), eq(DESCRIPTOR));
        assertThat(faceIdCaptor.getValue()).isEqualTo(result.faceId());
    }
}

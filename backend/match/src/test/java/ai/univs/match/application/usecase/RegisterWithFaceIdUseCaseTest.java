package ai.univs.match.application.usecase;

import ai.univs.match.application.input.RegisterWithFaceIdInput;
import ai.univs.match.application.result.MatchResult;
import ai.univs.match.application.service.RegisterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@DisplayName("RegisterWithFaceIdUseCase")
@ExtendWith(MockitoExtension.class)
class RegisterWithFaceIdUseCaseTest {

    @Mock
    private RegisterService registerService;

    @InjectMocks
    private RegisterWithFaceIdUseCase registerWithFaceIdUseCase;

    @Test
    @DisplayName("input의 branchName, faceId, descriptor를 RegisterService에 그대로 전달한다")
    void whenExecuted_thenDelegatesAllFieldsToRegisterService() {
        var input = new RegisterWithFaceIdInput("testBranch", "face-001", "descriptor-value");

        registerWithFaceIdUseCase.execute(input);

        verify(registerService).register("testBranch", "face-001", "descriptor-value");
    }

    @Test
    @DisplayName("실제 저장에 사용된 branchName을 MatchResult에 담아 반환한다")
    void whenExecuted_thenReturnsMatchResultWithBranchName() {
        var input = new RegisterWithFaceIdInput("testBranch", "face-001", "descriptor-value");

        MatchResult result = registerWithFaceIdUseCase.execute(input);

        assertThat(result.branchName()).isEqualTo("testBranch");
    }

    @Test
    @DisplayName("실제 저장에 사용된 faceId를 MatchResult에 담아 반환한다")
    void whenExecuted_thenReturnsMatchResultWithFaceId() {
        var input = new RegisterWithFaceIdInput("testBranch", "face-001", "descriptor-value");

        MatchResult result = registerWithFaceIdUseCase.execute(input);

        assertThat(result.faceId()).isEqualTo("face-001");
    }
}

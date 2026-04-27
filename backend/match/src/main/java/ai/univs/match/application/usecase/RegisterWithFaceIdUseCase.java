package ai.univs.match.application.usecase;

import ai.univs.match.application.input.RegisterWithFaceIdInput;
import ai.univs.match.application.result.MatchResult;
import ai.univs.match.application.service.RegisterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RegisterWithFaceIdUseCase {

    private final RegisterService registerService;

    @Transactional
    public MatchResult execute(RegisterWithFaceIdInput input) {
        registerService.register(
                input.branchName(),
                input.faceId(),
                input.descriptor());

        return new MatchResult(input.branchName(), input.faceId());
    }
}

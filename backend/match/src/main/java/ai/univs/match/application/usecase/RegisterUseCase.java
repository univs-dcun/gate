package ai.univs.match.application.usecase;

import ai.univs.match.application.result.MatchResult;
import ai.univs.match.application.service.RegisterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RegisterUseCase {

    private final RegisterService registerService;

    @Transactional
    public MatchResult execute(String branchName, String descriptor) {
        String faceId = UUID.randomUUID().toString();
        registerService.register(
                branchName,
                faceId,
                descriptor
        );

        return new MatchResult(branchName, faceId);
    }
}

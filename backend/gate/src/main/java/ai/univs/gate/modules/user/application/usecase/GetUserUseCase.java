package ai.univs.gate.modules.user.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.user.application.input.GetUserInput;
import ai.univs.gate.modules.user.application.result.UserResult;
import ai.univs.gate.modules.user.domain.entity.User;
import ai.univs.gate.modules.user.domain.repository.UserRepository;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetUserUseCase {

    private final UserRepository userRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;

    @Transactional(readOnly = true)
    public UserResult execute(GetUserInput input) {
        User user = userRepository.findByIdAndIsDeletedFalse(input.userId())
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();
        if (!user.getProject().equals(project)) {
            log.error("Not user who created based on this apikey. accountId: {}, apyKey: {}, userId: {}",
                    input.accountId(),
                    input.apiKey(),
                    input.userId());
            throw new CustomGateException(ErrorType.INVALID_USER);
        }

        return UserResult.from(user, fileService.getFileServerPath());
    }
}

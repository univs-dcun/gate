package ai.univs.gate.modules.user.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.user.application.input.GetUserByFaceId;
import ai.univs.gate.modules.user.application.result.UserResult;
import ai.univs.gate.modules.user.domain.entity.User;
import ai.univs.gate.modules.user.domain.repository.UserRepository;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetUserByFaceIdUseCase {

    private final UserRepository userRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;

    @Transactional(readOnly = true)
    public UserResult execute(GetUserByFaceId input) {
        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();
        User user = userRepository.findByFaceIdAndProjectIdAndIsDeletedFalse(input.faceId(), project.getId())
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        return UserResult.from(user, fileService.getFileServerPath());
    }
}

package ai.univs.gate.facade.demo.application.usecase;

import ai.univs.gate.facade.demo.application.input.CreateUserByApiKeyInput;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.user.application.result.UserResult;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import ai.univs.gate.support.user.CreateUserServiceResult;
import ai.univs.gate.support.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateUserByApiKeyUseCase {

    private final UserService userService;
    private final FileService fileService;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional
    public UserResult execute(CreateUserByApiKeyInput input) {
        ApiKey findApiKey = apiKeyService.findByApiKey(input.apiKey());

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(findApiKey.getProject());

        // 데모 활성화 여부 체크
        projectSettingsService.validateDemoEnabled(findProjectSettings);

        CreateUserServiceResult result = userService.createUser(
                CallerType.DEMO,
                input.accountId(),
                input.apiKey(),
                input.faceImage(),
                input.userDescription(),
                input.username(),
                input.transactionUuid());
        return UserResult.from(result.user(), result.livenessChecked(), fileService.getFileServerPath());
    }
}

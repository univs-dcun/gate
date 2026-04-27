package ai.univs.gate.facade.sdk.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.project.application.result.ProjectSettingsResult;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetSdkProjectConfigUseCase {

    private final ApiKeyService apiKeyService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(readOnly = true)
    public ProjectSettingsResult execute(String apiKey, String timezone) {
        ApiKey findApiKey = apiKeyService.findByApiKey(apiKey);

        ProjectSettings settings = projectSettingsService.findByProject(findApiKey.getProject());

        return ProjectSettingsResult.from(settings, timezone);
    }
}

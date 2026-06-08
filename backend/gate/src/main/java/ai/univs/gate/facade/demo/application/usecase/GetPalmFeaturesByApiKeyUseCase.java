package ai.univs.gate.facade.demo.application.usecase;

import ai.univs.gate.facade.demo.application.input.GetUsersByApiKeyInput;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.palm_feature.application.input.PalmFeatureQuery;
import ai.univs.gate.modules.palm_feature.application.result.GetPalmFeaturesResult;
import ai.univs.gate.modules.palm_feature.application.result.PalmFeatureResult;
import ai.univs.gate.modules.palm_feature.domain.entity.PalmFeature;
import ai.univs.gate.modules.palm_feature.domain.repository.PalmFeatureRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.usecase.result.CustomPageResult;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GetPalmFeaturesByApiKeyUseCase {

    private final PalmFeatureRepository palmFeatureRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(readOnly = true)
    public GetPalmFeaturesResult execute(GetUsersByApiKeyInput input) {
        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();

        ProjectSettings projectSettings = projectSettingsService.findByProject(project);

        PalmFeatureQuery query = new PalmFeatureQuery(
                null,
                input.apiKey(),
                input.userKeyword(),
                input.page(),
                input.pageSize(),
                input.isDeleted(),
                input.startDate(),
                input.endDate(),
                "DESC",
                "palmFeatureId"
        );

        long totalCount = palmFeatureRepository.countByProjectIdAndIsDeletedFalse(project.getId());
        Page<PalmFeature> palmFeatures = palmFeatureRepository.findAllByQuery(query, project.getId());

        if (palmFeatures.isEmpty()) {
            return new GetPalmFeaturesResult(Collections.emptyList(), CustomPageResult.of(Page.empty(), totalCount));
        }

        boolean consentEnabled = projectSettings.getConsentEnabled();
        List<PalmFeatureResult> contents = palmFeatures.stream()
                .map(pm -> PalmFeatureResult.from(pm, fileService.getFileServerPath(), consentEnabled))
                .toList();

        CustomPageResult page = CustomPageResult.from(
                palmFeatures.getPageable(),
                palmFeatures.getTotalElements(),
                palmFeatures.getTotalPages(),
                totalCount);

        return new GetPalmFeaturesResult(contents, page);
    }
}

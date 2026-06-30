package ai.univs.gate.modules.feature.application.usecase.palm;

import ai.univs.gate.modules.feature.application.input.BiometricFeatureQuery;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.feature.application.input.palm.PalmFeatureQuery;
import ai.univs.gate.modules.feature.application.result.palm.GetPalmFeaturesResult;
import ai.univs.gate.modules.feature.application.result.palm.PalmFeatureResult;
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
public class GetPalmFeaturesUseCase {

    private final BiometricFeatureRepository biometricFeatureRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(readOnly = true)
    public GetPalmFeaturesResult execute(PalmFeatureQuery query) {
        var apiKey = apiKeyService.findByApiKey(query.apiKey());
        Project project = apiKey.getProject();
        ProjectSettings settings = projectSettingsService.findByProject(project);

        long totalCount = biometricFeatureRepository.countByProjectIdAndTypeAndIsDeletedFalse(project.getId(), FeatureType.PALM);

        BiometricFeatureQuery biometricQuery = new BiometricFeatureQuery(
                query.accountId(), query.apiKey(), FeatureType.PALM, query.keyword(),
                query.page(), query.pageSize(), query.isDeleted(),
                query.startDate(), query.endDate(), "DESC", "palmFeatureId");

        Page<BiometricFeature> page = biometricFeatureRepository.findAllByQuery(biometricQuery, project.getId());

        if (page.isEmpty()) {
            return new GetPalmFeaturesResult(Collections.emptyList(),
                    CustomPageResult.of(org.springframework.data.domain.Page.empty(), totalCount));
        }

        boolean consentEnabled = settings.getConsentEnabled();
        List<PalmFeatureResult> contents = page.stream()
                .map(pm -> PalmFeatureResult.from(pm, fileService.getFileServerPath(), consentEnabled))
                .toList();

        CustomPageResult pageResult = CustomPageResult.from(
                page.getPageable(),
                page.getTotalElements(),
                page.getTotalPages(),
                totalCount);

        return new GetPalmFeaturesResult(contents, pageResult);
    }
}

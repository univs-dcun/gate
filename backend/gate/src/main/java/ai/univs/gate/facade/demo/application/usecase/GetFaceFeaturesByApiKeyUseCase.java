package ai.univs.gate.facade.demo.application.usecase;

import ai.univs.gate.facade.demo.application.input.GetUsersByApiKeyInput;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.face_feature.application.input.FaceFeatureQuery;
import ai.univs.gate.modules.face_feature.application.result.FaceFeatureResult;
import ai.univs.gate.modules.face_feature.application.result.GetFaceFeaturesResult;
import ai.univs.gate.modules.face_feature.domain.entity.FaceFeature;
import ai.univs.gate.modules.face_feature.domain.repository.FaceFeatureRepository;
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
public class GetFaceFeaturesByApiKeyUseCase {

    private final FaceFeatureRepository faceFeatureRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(readOnly = true)
    public GetFaceFeaturesResult execute(GetUsersByApiKeyInput input) {
        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();

        ProjectSettings projectSettings = projectSettingsService.findByProject(project);

        FaceFeatureQuery query = input.toFaceFeatureQuery();
        long totalCount = faceFeatureRepository.countByProjectIdAndIsDeletedFalse(project.getId());
        Page<FaceFeature> faceFeatures = faceFeatureRepository.findAllByQuery(query, project.getId());

        if (faceFeatures.isEmpty()) {
            return new GetFaceFeaturesResult(Collections.emptyList(), CustomPageResult.of(Page.empty(), totalCount));
        }

        boolean consentEnabled = projectSettings.getConsentEnabled();
        List<FaceFeatureResult> contents = faceFeatures.stream()
                .map(fm -> FaceFeatureResult.from(fm, fileService.getFileServerPath(), consentEnabled))
                .toList();

        CustomPageResult page = CustomPageResult.from(
                faceFeatures.getPageable(),
                faceFeatures.getTotalElements(),
                faceFeatures.getTotalPages(),
                totalCount);

        return new GetFaceFeaturesResult(contents, page);
    }
}

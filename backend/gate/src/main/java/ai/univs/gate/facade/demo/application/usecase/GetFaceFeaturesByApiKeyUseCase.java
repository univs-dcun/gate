package ai.univs.gate.facade.demo.application.usecase;

import ai.univs.gate.facade.demo.application.input.GetUsersByApiKeyInput;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.face_feature.application.input.FaceFeatureQuery;
import ai.univs.gate.modules.face_feature.application.result.FaceFeatureResult;
import ai.univs.gate.modules.face_feature.application.result.GetFaceFeaturesResult;
import ai.univs.gate.modules.feature.application.input.BiometricFeatureQuery;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
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

    private final BiometricFeatureRepository biometricFeatureRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(readOnly = true)
    public GetFaceFeaturesResult execute(GetUsersByApiKeyInput input) {
        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();

        ProjectSettings projectSettings = projectSettingsService.findByProject(project);

        FaceFeatureQuery query = input.toFaceFeatureQuery();
        long totalCount = biometricFeatureRepository.countByProjectIdAndTypeAndIsDeletedFalse(project.getId(), FeatureType.FACE);

        BiometricFeatureQuery biometricQuery = new BiometricFeatureQuery(
                query.accountId(), query.apiKey(), FeatureType.FACE, query.keyword(),
                query.page(), query.pageSize(), query.isDeleted(),
                query.startDate(), query.endDate(), query.direction(), query.sortBy());

        Page<BiometricFeature> faceFeatures = biometricFeatureRepository.findAllByQuery(biometricQuery, project.getId());

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

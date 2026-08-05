package ai.univs.gate.modules.feature.application.usecase.palm;

import ai.univs.gate.modules.feature.application.input.palm.CreatePalmFeatureInput;
import ai.univs.gate.modules.feature.application.result.palm.PalmFeatureResult;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.feature.palm.CreatePalmFeatureServiceResult;
import ai.univs.gate.support.feature.palm.PalmFeatureService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ai.univs.gate.shared.web.enums.CallerType;

@Component
@RequiredArgsConstructor
public class CreatePalmFeatureUseCase {

    private final PalmFeatureService palmFeatureService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    public PalmFeatureResult execute(CreatePalmFeatureInput input) {
        CreatePalmFeatureServiceResult result = palmFeatureService.createPalmFeature(
                CallerType.API,
                input.accountId(),
                input.apiKey(),
                input.featureImage(),
                input.description(),
                input.transactionUuid());

        // UG-281 반박 리뷰: CreateFaceFeatureUseCase 와 같은 이유로 재조회하지 않는다.
        ProjectSettings settings = projectSettingsService.findByProject(
                result.biometricFeature().getProject());

        return PalmFeatureResult.from(result.biometricFeature(), result.livenessChecked(),
                fileService.getFileServerPath(), settings.getConsentEnabled());
    }
}

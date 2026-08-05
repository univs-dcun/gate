package ai.univs.gate.modules.feature.application.usecase.palm;

import ai.univs.gate.modules.feature.application.input.palm.GetPalmFeatureInput;
import ai.univs.gate.modules.feature.application.result.palm.PalmFeatureResult;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.utils.ApiKeyMasker;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetPalmFeatureUseCase {

    private final BiometricFeatureRepository biometricFeatureRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(readOnly = true)
    public PalmFeatureResult execute(GetPalmFeatureInput input) {
        BiometricFeature biometricFeature = biometricFeatureRepository.findByIdAndTypeAndIsDeletedFalse(input.palmFeatureId(), FeatureType.PALM)
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        var apiKey = apiKeyService.findOwnedByApiKey(input.apiKey(), input.accountId());
        Project project = apiKey.getProject();

        // UG-281 반박 리뷰: 이 검사가 없었다. 키 소유만 확인하고 조회한 특징점이 그 키의
        // 프로젝트 것인지는 보지 않아, 순번 ID(IDENTITY)를 훑는 것만으로 남의 팜 특징점을
        // 읽을 수 있었다. Face 단건 조회·Palm 삭제·Palm 수정에는 같은 검사가 있고
        // 이 경로에만 빠져 있었다.
        //
        // 특히 위험했던 이유: consentEnabled 를 '호출자' 프로젝트 설정에서 가져오므로,
        // 공격자가 자기 프로젝트의 동의만 켜면 피해자가 동의를 껐어도 featureImagePath 가
        // 채워져 나갔다. 그 경로는 /api/v1/file 이 무인증으로 서빙한다.
        if (!biometricFeature.getProject().equals(project)) {
            log.error("Not palmFeature who created based on this apikey. accountId: {}, apiKey: {}, palmFeatureId: {}",
                    input.accountId(), ApiKeyMasker.mask(input.apiKey()), input.palmFeatureId());
            throw new CustomGateException(ErrorType.INVALID_USER);
        }

        ProjectSettings settings = projectSettingsService.findByProject(project);

        return PalmFeatureResult.from(biometricFeature, fileService.getFileServerPath(), settings.getConsentEnabled());
    }
}

package ai.univs.gate.modules.feature.application.usecase.palm;

import ai.univs.gate.modules.feature.application.result.palm.PalmFeatureResult;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.UpdateFeatureDescriptionInput;
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

/**
 * 팜 특징점의 메모(description)만 바꾼다 (UG-376).
 *
 * <p><b>왜 메모만인가.</b> 특징점 변경은 정책상 두지 않는다 — 바꾸려면 삭제 후 다시 등록한다(UG-332 에서 수정 API 제거,
 * 2026-10-12 재확인). 콘솔 개인정보 동의 화면의 「메모 수정」이 이 API 를 쓴다.
 *
 * <p><b>이력을 남기지 않는다.</b> 메모는 gate 에만 있는 표시용 값이라 하위 서비스를 부르지 않고, 생체 인증 처리도 아니다 —
 * 등록·삭제처럼 feature_history 에 행을 남기지 않는다. 바뀐 시각은 {@code modified_at} 에 남는다.
 *
 * <p>조회·소유 검증은 단건 조회와 같다: 지워졌거나 다른 종류거나 이 키의 프로젝트 것이 아니면 모두 {@code INVALID_USER} —
 * 남의 특징점이 있는지조차 알려 주지 않는다. 응답도 단건 조회와 같은 모양이라, 콘솔이 결과를 알 수 없을 때 단건 조회로 메모를
 * 비교해 확인할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdatePalmFeatureDescriptionUseCase {

    private final BiometricFeatureRepository biometricFeatureRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional
    public PalmFeatureResult execute(UpdateFeatureDescriptionInput input) {
        BiometricFeature feature = biometricFeatureRepository.findByIdAndTypeAndIsDeletedFalse(input.featureId(), FeatureType.PALM)
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ApiKey apiKey = apiKeyService.findOwnedByApiKey(input.apiKey(), input.accountId());
        Project project = apiKey.getProject();
        // id 로 비교한다 — 키 조회는 요청마다 프로젝트 사본을 준다(UG-364)
        if (!feature.getProject().getId().equals(project.getId())) {
            log.error("Not palmFeature who created based on this apikey. accountId: {}, apiKey: {}, featureId: {}",
                    input.accountId(), ApiKeyMasker.mask(input.apiKey()), input.featureId());
            throw new CustomGateException(ErrorType.INVALID_USER);
        }

        feature.changeDescription(input.description());

        ProjectSettings settings = projectSettingsService.findByProject(project);
        return PalmFeatureResult.from(feature, fileService.getFileServerPath(), settings.getConsentEnabled());
    }
}

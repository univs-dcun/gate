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
import org.springframework.transaction.annotation.Transactional;
import ai.univs.gate.shared.web.enums.CallerType;

@Component
@RequiredArgsConstructor
public class CreatePalmFeatureUseCase {

    private final PalmFeatureService palmFeatureService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    /**
     * 쌍둥이인 {@code CreateFaceFeatureUseCase} 와 같이 트랜잭션을 연다 (UG-335 반박 리뷰).
     *
     * <p>아래 두 줄이 지연 연관을 건드린다 — {@code getProject()} 로 프로젝트 설정을 찾고,
     * {@code PalmFeatureResult.from} 이 다시 {@code getProject().getId()} 를 읽는다.
     * {@code PalmFeatureService.createPalmFeature} 는 {@code REQUIRES_NEW} 라 그 안의
     * 영속성 컨텍스트가 여기 오기 전에 닫힌다.
     *
     * <p>지금까지 터지지 않은 것은 <b>우연</b>이었다. 그 서비스가 내부에서
     * {@code project.getBranchName()} 을 읽어 프록시를 미리 초기화해 둔 덕이다. 그 한 줄이
     * 사라지면 조용히 깨진다. face 쪽만 선언이 있던 비대칭도 함께 해소한다.
     */
    @Transactional
    public PalmFeatureResult execute(CreatePalmFeatureInput input) {
        CreatePalmFeatureServiceResult result = palmFeatureService.createPalmFeature(
                CallerType.API,
                input.accountId(),
                input.apiKey(),
                input.featureImage(),
                input.description(),
                input.transactionUuid(),
                input.externalKey());

        // UG-281 반박 리뷰: CreateFaceFeatureUseCase 와 같은 이유로 재조회하지 않는다.
        ProjectSettings settings = projectSettingsService.findByProject(
                result.biometricFeature().getProject());

        return PalmFeatureResult.from(result.biometricFeature(), result.livenessChecked(),
                fileService.getFileServerPath(), settings.getConsentEnabled());
    }
}

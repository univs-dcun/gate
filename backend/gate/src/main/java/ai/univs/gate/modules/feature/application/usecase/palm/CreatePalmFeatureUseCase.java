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

    /**
     * <b>트랜잭션을 열지 않는다 (UG-336).</b> 예전에는 열었다(UG-335 반박 리뷰) — 그러면 그
     * 트랜잭션이 첫 조회에서 잡은 커넥션을 palm 원격 호출 내내 붙들고, 안쪽의
     * {@code HistoryRecorder.start} 가 두 번째 커넥션을 요구했다. 경계는
     * {@code PalmFeatureService.createPalmFeature} 안에서 단계별로 열린다.
     *
     * <p>당시 트랜잭션을 연 이유는 아래 두 줄의 지연 연관이었다 — {@code getProject()} 로
     * 프로젝트 설정을 찾고, {@code PalmFeatureResult.from} 이 {@code getProject().getId()} 를
     * 읽는다. 지금은 트랜잭션 없이도 안전하다. 특징점의 {@code project} 는 {@code ApiKeyService}
     * 가 <b>초기화해서</b> 돌려준 객체이고(UG-335 — 인증 경로는 소유 검증이, 데모 경로는
     * {@code Hibernate.initialize} 가 한다), {@code findByProject} 는 id 로 조회하며,
     * {@code getId()} 는 프록시여도 초기화 없이 답한다.
     */
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

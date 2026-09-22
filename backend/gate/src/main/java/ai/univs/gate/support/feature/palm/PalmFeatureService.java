package ai.univs.gate.support.feature.palm;

import ai.univs.gate.support.feature.face.FaceFeatureService;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.domain.repository.FeatureHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.RegisterPalmFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ai.univs.gate.shared.web.enums.CallerType;

@Service
@RequiredArgsConstructor
public class PalmFeatureService {

    private final BiometricFeatureRepository biometricFeatureRepository;
    private final FeatureHistoryRepository featureHistoryRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final PalmService palmService;
    private final ProjectSettingsService projectSettingsService;

    /**
     * @param callerType 무인증 데모({@link CallerType#DEMO})는 대조할 accountId 가 없어 소유 검증을
     *                   건너뛴다. 인증 경로는 반드시 {@link CallerType#API} 를 넘긴다. (UG-281)
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            // UG-280: RemoteCallException 이 목록에 있어야 하위 서비스 5xx 에도
            // 매칭 이력 행이 커밋된다. CustomGateException 을 넣지 않는 이유는
            // 그러면 모든 비즈니스 예외에 커밋을 허용해 버리기 때문이다.
            noRollbackFor = {CustomFeignException.class, RemoteCallException.class}
    )
    public CreatePalmFeatureServiceResult createPalmFeature(CallerType callerType,
                                                            Long accountId,
                                                            String apiKey,
                                                            MultipartFile featureImage,
                                                            String description,
                                                            String transactionUuid,
                                                            String externalKey
    ) {
        // UG-281: FaceFeatureService.createFaceFeature 와 같은 이유로 맨 앞에서 검증한다.
        ApiKey findApiKey = apiKeyService.findByApiKey(callerType, apiKey, accountId);
        Project project = findApiKey.getProject();

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);

        String imagePath = fileService.uploadIfConsent(featureImage, findProjectSettings.getConsentEnabled());

        // UG-325/326: 등록은 인증 시도가 아니라 특징점의 생애주기 사건이다 — feature_history 에만 쓴다.
        // (UG-325 의 과도기 이중 기록은 통합 조회가 나가면서 끝났고, V27 이 옛 REGISTER 행을 지웠다.)
        FeatureHistory featureHistory = featureHistoryRepository.save(FeatureHistory.register(
                project, FeatureType.PALM, projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.PALM, LivenessOperation.REGISTER), imagePath, transactionUuid,
                findProjectSettings.getConsentEnabled()));

        var registerRequest = new RegisterPalmFeignRequestDTO(
                project.getBranchName(),
                featureImage,
                transactionUuid,
                // UG-277 반박 리뷰: 등록은 데모로도 도달하므로 호출자 accountId 를 그대로 보낸다.
                // 데모 DTO 가 0L 을 넘기고, 그 "0" 이 face/palm 이력에서 데모에서 온 행임을
                // 알려주는 유일한 흔적이다. 소유자 id 로 통일하면 데모 등록과 인증 등록이
                // 구분되지 않는다 — 은행권 e-KYC 에서 감사 해상도가 떨어진다.
                // 인증 경로에서는 소유 검증(ENFORCE)이 호출자 == 소유자를 보장하므로 값이 같다.
                String.valueOf(accountId),
                projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.PALM, LivenessOperation.REGISTER));

        String palmId;
        try {
            palmId = palmService.registerPalm(registerRequest);
        } catch (CustomFeignException e) {
            featureHistory.fail(e.getType());
            throw e;
        } catch (RemoteCallException e) {
            // UG-280: 하위 서비스 5xx. 예전에는 CustomGateException 이라 noRollbackFor 에
            // 걸리지 않아 트랜잭션이 롤백되고 이 이력 행 자체가 사라졌다.
            featureHistory.failUpstream(e.getErrorType().name(), e.getUpstreamStatus());
            throw e;
        }

        BiometricFeature biometricFeature = BiometricFeature.builder()
                .project(project)
                .type(FeatureType.PALM)
                .featureId(palmId)
                .featureImagePath(imagePath)
                .description(description)
                .isDeleted(false)
                .transactionUuid(transactionUuid)
                // UG-333: 예전에는 요청 DTO 가 받기만 하고 여기서 버렸다 — 이제 저장한다.
                .externalKey(FaceFeatureService.normalizeExternalKey(externalKey))
                .build();
        biometricFeatureRepository.save(biometricFeature);

        featureHistory.successRegister(biometricFeature);

        return new CreatePalmFeatureServiceResult(biometricFeature, projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.PALM, LivenessOperation.REGISTER));
    }

    public BiometricFeature getPalmFeatureByPalmIdAndProjectId(String featureId, Long projectId) {
        return biometricFeatureRepository.findByFeatureIdAndProjectIdAndTypeAndIsDeletedFalse(featureId, projectId, FeatureType.PALM)
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));
    }
}

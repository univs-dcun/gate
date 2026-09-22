package ai.univs.gate.support.feature.palm;

import ai.univs.gate.support.feature.face.FaceFeatureService;
import ai.univs.gate.support.history.HistoryRecorder;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
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

    private final HistoryRecorder historyRecorder;
    private final BiometricFeatureRepository biometricFeatureRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final PalmService palmService;
    private final ProjectSettingsService projectSettingsService;

    /**
     * @param callerType 무인증 데모({@link CallerType#DEMO})는 대조할 accountId 가 없어 소유 검증을
     *                   건너뛴다. 인증 경로는 반드시 {@link CallerType#API} 를 넘긴다. (UG-281)
      *
     * <p><b>UG-293: 이력 커밋이 이 트랜잭션과 분리됐다.</b> 예전에는 {@code noRollbackFor} 로
     * "이 예외들에서는 롤백하지 말라" 고 열거했고, 목록에 없는 예외 — 특히 우리 코드의 NPE —
     * 에서는 이력이 그대로 사라졌다. 지금은 {@link HistoryRecorder} 가 행을 먼저 커밋한다.
     *
     * <p><b>전파도 함께 바뀌었다: {@code REQUIRES_NEW} → {@code REQUIRED}.</b> 이력이 더는 이
     * 트랜잭션에 묶여 있지 않으므로 경계를 따로 열 이유가 없어졌고, 호출자와 합류하는 편이
     * 특징점 저장의 원자성에 맞다. 성공 이력도 {@code succeed} 로 이 트랜잭션에 합류하므로
     * "특징점은 롤백됐는데 등록 성공 이력만 남는" 상태가 생기지 않는다 (반박 리뷰 지적).
     * 실패 이력만 별도 트랜잭션으로 빠져나간다 — 그것이 이 티켓의 목적이다.
     */
    @Transactional
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
        FeatureHistory featureHistory = historyRecorder.start(FeatureHistory.register(
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
            historyRecorder.fail(featureHistory);
            throw e;
        } catch (RemoteCallException e) {
            // UG-280: 하위 서비스 5xx. 예전에는 CustomGateException 이라 noRollbackFor 에
            // 걸리지 않아 트랜잭션이 롤백되고 이 이력 행 자체가 사라졌다.
            featureHistory.failUpstream(e);
            historyRecorder.fail(featureHistory);
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
        historyRecorder.succeed(featureHistory);

        return new CreatePalmFeatureServiceResult(biometricFeature, projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.PALM, LivenessOperation.REGISTER));
    }

    public BiometricFeature getPalmFeatureByPalmIdAndProjectId(String featureId, Long projectId) {
        return biometricFeatureRepository.findByFeatureIdAndProjectIdAndTypeAndIsDeletedFalse(featureId, projectId, FeatureType.PALM)
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));
    }
}

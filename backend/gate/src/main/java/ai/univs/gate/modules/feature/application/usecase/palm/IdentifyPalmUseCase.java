package ai.univs.gate.modules.feature.application.usecase.palm;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.palm.PalmIdentifyInput;
import ai.univs.gate.modules.feature.application.result.palm.PalmIdentifyResult;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.feature.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.IdentifyPalmFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.IdentifyPalmFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.palm.PalmFeatureService;
import ai.univs.gate.support.feature.palm.PalmService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class IdentifyPalmUseCase {

    private final MatchHistoryRepository matchHistoryRepository;
    private final ProjectSettingsService projectSettingsService;
    private final PalmFeatureService palmFeatureService;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final PalmService palmService;
    private final BiometricFeatureRepository biometricFeatureRepository;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            // UG-280: RemoteCallException 이 목록에 있어야 하위 서비스 5xx 에도
            // 매칭 이력 행이 커밋된다. CustomGateException 을 넣지 않는 이유는
            // 그러면 모든 비즈니스 예외에 커밋을 허용해 버리기 때문이다.
            noRollbackFor = {CustomFeignException.class, RemoteCallException.class}
    )
    public PalmIdentifyResult execute(PalmIdentifyInput input) {
        ApiKey findApiKey = apiKeyService.findByApiKey(input.callerType(), input.apiKey(), input.accountId());
        Project project = findApiKey.getProject();

        ProjectSettings projectSettings = projectSettingsService.findByProject(project);

        boolean consentEnabled = projectSettings.getConsentEnabled();

        // 등록된 팜 사용자가 없으면 사전 차단
        if (biometricFeatureRepository.countByProjectIdAndTypeAndIsDeletedFalse(project.getId(), FeatureType.PALM) == 0) {
            var imagePath = fileService.uploadIfConsent(input.featureImage(), consentEnabled);
            MatchHistory preCheckHistory = MatchHistory.builder()
                    .project(project)
                    .matchType(MatchType.IDENTIFY)
                    .featureType(FeatureType.PALM)
                    .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                    .checkLiveness(projectSettingsService.isLivenessEnabled(projectSettings, FeatureType.PALM, LivenessOperation.IDENTIFY))
                    .success(false)
                    .matchedFeatureImagePath(imagePath)
                    .transactionUuid(input.transactionUuid())
                    .consentSnapshot(consentEnabled)
                    .build();
            matchHistoryRepository.save(preCheckHistory);
            preCheckHistory.fail(BigDecimal.ZERO, "NO_REGISTERED_PALM_USERS");
            return PalmIdentifyResult.failResult(preCheckHistory, "NO_REGISTERED_PALM_USERS",
                    fileService.getFileServerPath(), consentEnabled);
        }

        var imagePath = fileService.uploadIfConsent(input.featureImage(), consentEnabled);

        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.IDENTIFY)
                .featureType(FeatureType.PALM)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(projectSettingsService.isLivenessEnabled(projectSettings, FeatureType.PALM, LivenessOperation.IDENTIFY))
                .success(false)
                .matchedFeatureImagePath(imagePath)
                .transactionUuid(input.transactionUuid())
                .consentSnapshot(consentEnabled)
                .build();
        matchHistoryRepository.save(matchHistory);

        var identifyRequest = new IdentifyPalmFeignRequestDTO(
                project.getBranchName(),
                input.featureImage(),
                input.transactionUuid(),
                // UG-277 반박 리뷰: 여기는 호출자 accountId 를 그대로 보낸다. 프로젝트 소유자로 바꾸면
                // 안 된다 — 무인증 데모 DTO 가 0L 을 넘기고, 그 "0" 이 face/palm 이력에서
                // <b>데모에서 온 행임을 알려주는 유일한 흔적</b>이다. gate 의 MatchHistory 에는
                // callerType·accountId 컬럼이 없고 face/palm 에도 호출자 필드가 없다. 소유자 id 로
                // 통일하면 데모 등록과 인증 등록이 바이트 단위로 같아져 출처를 되찾을 수 없다.
                // 은행권 e-KYC 에서 감사 해상도를 떨어뜨리는 변경이므로 하지 않는다.
                // 인증 경로에서는 소유 검증(ENFORCE)이 호출자 == 소유자를 보장하므로 값이 같다.
                input.accountId().toString(),
                projectSettingsService.isLivenessEnabled(projectSettings, FeatureType.PALM, LivenessOperation.IDENTIFY));

        String prefixImagePath = fileService.getFileServerPath();

        IdentifyPalmFeignResponseDTO data;
        try {
            data = palmService.identify(identifyRequest);
        } catch (CustomFeignException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getType());
            return PalmIdentifyResult.failResult(matchHistory, e.getType(), prefixImagePath, consentEnabled);
        } catch (RemoteCallException e) {
            // UG-280: 하위 서비스 5xx. 예전에는 CustomGateException 이라 noRollbackFor 에
            // 걸리지 않아 트랜잭션이 롤백되고 이 이력 행 자체가 사라졌다.
            matchHistory.fail(BigDecimal.ZERO, e.getErrorType().name());
            throw e;
        }

        if (!data.isResult()) {
            matchHistory.fail(parseSimilarity(data.getSimilarity()), "PALM_NOT_MATCH");
            return PalmIdentifyResult.failResult(matchHistory, "PALM_NOT_MATCH", prefixImagePath, consentEnabled);
        }

        BiometricFeature biometricFeature;
        try {
            biometricFeature = palmFeatureService.getPalmFeatureByPalmIdAndProjectId(data.getPalmId(), project.getId());
        } catch (CustomGateException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getErrorType().name());
            return PalmIdentifyResult.failResult(matchHistory, e.getErrorType().name(), prefixImagePath, consentEnabled);
        }

        BigDecimal similarity = parseSimilarity(data.getSimilarity());
        matchHistory.success(biometricFeature, similarity);

        return PalmIdentifyResult.successResult(matchHistory, biometricFeature, matchHistory.getSimilarity(), data.getThreshold(), prefixImagePath, consentEnabled);
    }

    private BigDecimal parseSimilarity(String similarity) {
        try {
            // Palm 서비스는 similarity를 퍼센트(0~100)로 반환.
            // MatchHistory.toPercent()가 × 100을 하므로 미리 ÷ 100 처리.
            return new BigDecimal(similarity)
                    .divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}

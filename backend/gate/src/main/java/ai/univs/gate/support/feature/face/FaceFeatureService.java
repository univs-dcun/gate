package ai.univs.gate.support.feature.face;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.CreateFaceByDescriptorFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.CreateFaceFeignRequestDTO;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.shared.exception.CustomFeignException;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class FaceFeatureService {

    private final BiometricFeatureRepository biometricFeatureRepository;
    private final MatchHistoryRepository matchHistoryRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final FaceService faceService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = CustomFeignException.class
    )
    public CreateFaceFeatureServiceResult createFaceFeature(Long accountId,
                                                            String apiKey,
                                                            MultipartFile featureImage,
                                                            String description,
                                                            String transactionUuid
    ) {
        ApiKey findApiKey = apiKeyService.findByApiKey(apiKey);
        Project project = findApiKey.getProject();

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);

        String imagePath = fileService.uploadIfConsent(featureImage, findProjectSettings.getConsentEnabled());

        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.REGISTER)
                .featureType(FeatureType.FACE)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.FACE, LivenessOperation.REGISTER))
                .success(false)
                .matchedFeatureImagePath(imagePath)
                .transactionUuid(transactionUuid)
                .consentSnapshot(findProjectSettings.getConsentEnabled())
                .build();
        matchHistoryRepository.save(matchHistory);

        var createRequest = new CreateFaceFeignRequestDTO(
                project.getBranchName(),
                featureImage,
                transactionUuid,
                String.valueOf(accountId),
                projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.FACE, LivenessOperation.REGISTER),
                projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.FACE, LivenessOperation.REGISTER));
        String featureId;
        try {
            featureId = faceService.createFace(createRequest);
        } catch (CustomFeignException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getType());
            throw e;
        }

        BiometricFeature biometricFeature = BiometricFeature.builder()
                .project(project)
                .type(FeatureType.FACE)
                .featureId(featureId)
                .featureImagePath(imagePath)
                .description(description)
                .isDeleted(false)
                .transactionUuid(transactionUuid)
                .build();
        biometricFeatureRepository.save(biometricFeature);

        matchHistory.success(biometricFeature, BigDecimal.ZERO);

        return new CreateFaceFeatureServiceResult(biometricFeature, projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.FACE, LivenessOperation.REGISTER));
    }

    /**
     * descriptor 기반 특징점 얼굴 등록 (UG-279).
     *
     * <p>{@link #createFaceFeature} 와의 차이는 세 가지다.
     * <ul>
     *   <li>{@code fileService.uploadIfConsent(...)} 를 <b>호출하지 않는다.</b> 이미지 파일이
     *       없는데 호출하면 {@code FileUtil.save} 가 {@code file.getOriginalFilename()} 에서
     *       NPE 를 던진다 (동의 설정이 켜져 있고 file.enable.upload 가 true 인 모든 환경).</li>
     *   <li>{@code checkLiveness} 를 프로젝트 설정과 무관하게 {@code false} 로 고정한다.
     *       descriptor 를 가지고 있다는 것은 추출·라이브니스 단계가 이미 끝났다는 뜻이다.</li>
     *   <li>{@code description} 을 받지 않는다.</li>
     * </ul>
     *
     * <p>{@code consentSnapshot} 은 계속 저장한다. 응답에서만 빼기로 한 값이고, 이력 통계와
     * 기존 행과의 일관성을 위해 DB 에는 남기는 편이 맞다.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = CustomFeignException.class
    )
    public BiometricFeature createFaceFeatureByDescriptor(Long accountId,
                                                         String apiKey,
                                                         String descriptor,
                                                         String transactionUuid
    ) {
        ApiKey findApiKey = apiKeyService.findByApiKey(apiKey);
        Project project = findApiKey.getProject();

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);

        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.REGISTER)
                .featureType(FeatureType.FACE)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(false)
                .success(false)
                .transactionUuid(transactionUuid)
                .consentSnapshot(findProjectSettings.getConsentEnabled())
                .build();
        matchHistoryRepository.save(matchHistory);

        var createRequest = new CreateFaceByDescriptorFeignRequestDTO(
                project.getBranchName(),
                descriptor,
                transactionUuid,
                String.valueOf(accountId));
        String featureId;
        try {
            featureId = faceService.createFaceByDescriptor(createRequest);
        } catch (CustomFeignException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getType());
            throw e;
        }

        BiometricFeature biometricFeature = BiometricFeature.builder()
                .project(project)
                .type(FeatureType.FACE)
                .featureId(featureId)
                .isDeleted(false)
                .transactionUuid(transactionUuid)
                .build();
        biometricFeatureRepository.save(biometricFeature);

        matchHistory.success(biometricFeature, BigDecimal.ZERO);

        return biometricFeature;
    }

    public BiometricFeature getFaceFeatureByFaceIdAndProjectId(String featureId, Long projectId) {
        return biometricFeatureRepository.findByFeatureIdAndProjectIdAndTypeAndIsDeletedFalse(featureId, projectId, FeatureType.FACE)
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));
    }
}

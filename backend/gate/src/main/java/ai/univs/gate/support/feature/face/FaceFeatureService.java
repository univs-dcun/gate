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
import ai.univs.gate.shared.web.enums.CallerType;

@Service
@RequiredArgsConstructor
public class FaceFeatureService {

    private final BiometricFeatureRepository biometricFeatureRepository;
    private final MatchHistoryRepository matchHistoryRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final FaceService faceService;
    private final ProjectSettingsService projectSettingsService;

    /**
     * @param callerType 무인증 데모({@link CallerType#DEMO})는 대조할 accountId 가 없어 소유 검증을
     *                   건너뛴다. 인증 경로는 반드시 {@link CallerType#API} 를 넘긴다. (UG-281)
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = CustomFeignException.class
    )
    public CreateFaceFeatureServiceResult createFaceFeature(CallerType callerType,
                                                            Long accountId,
                                                            String apiKey,
                                                            MultipartFile featureImage,
                                                            String description,
                                                            String transactionUuid
    ) {
        // UG-281: 검증을 이 메서드 맨 앞에서 한다. 예전에는 호출하는 UseCase 가 등록을
        // 마친 뒤에야 소유를 확인했는데, 이 메서드는 REQUIRES_NEW 라 그 시점엔 이미 특징점과
        // 이력이 별도 트랜잭션으로 커밋된 뒤였다 — 거부해도 남의 갤러리에 얼굴이 남았다.
        ApiKey findApiKey = apiKeyService.findByApiKey(callerType, apiKey, accountId);
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
                // UG-277: 호출자 accountId 가 아니라 프로젝트 소유자 accountId 를 보낸다.
                // 무인증 데모는 accountId 로 0L 을 넘기므로 예전에는 모든 데모 등록이
                // face/palm 서비스에 createdBy="0" 으로 기록됐다. 이 값은 감사 필드로만
                // 쓰이고 조회 조건에는 쓰이지 않는다(파티셔닝은 branchName).
                String.valueOf(project.getAccountId()),
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
        // descriptor 등록은 인증 경로 전용이다 (데모에 대응 엔드포인트가 없다).
        ApiKey findApiKey = apiKeyService.findOwnedByApiKey(apiKey, accountId);
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
                // UG-277: 호출자 accountId 가 아니라 프로젝트 소유자 accountId 를 보낸다.
                // 무인증 데모는 accountId 로 0L 을 넘기므로 예전에는 모든 데모 등록이
                // face/palm 서비스에 createdBy="0" 으로 기록됐다. 이 값은 감사 필드로만
                // 쓰이고 조회 조건에는 쓰이지 않는다(파티셔닝은 branchName).
                String.valueOf(project.getAccountId()));
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

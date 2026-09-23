package ai.univs.gate.support.feature.face;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.CreateFaceByDescriptorFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.CreateFaceFeignRequestDTO;
import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.history.HistoryRecorder;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import ai.univs.gate.shared.web.enums.CallerType;

@Service
@RequiredArgsConstructor
public class FaceFeatureService {

    private final HistoryRecorder historyRecorder;
    private final BiometricFeatureRepository biometricFeatureRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final FaceService faceService;
    private final ProjectSettingsService projectSettingsService;
    private final TransactionTemplate transactionTemplate;

    /**
     * @param callerType 무인증 데모({@link CallerType#DEMO})는 대조할 accountId 가 없어 소유 검증을
     *                   건너뛴다. 인증 경로는 반드시 {@link CallerType#API} 를 넘긴다. (UG-281)
     *
     * <p><b>트랜잭션을 메서드 전체에 걸지 않는다 (UG-336).</b> 예전에는 {@code @Transactional}
     * 이 메서드 전체를 감쌌고, 그러면 첫 조회에서 잡은 커넥션을 커밋까지 붙든다 — face 서비스
     * <b>원격 호출 내내</b> 커넥션 하나가 묶이고, 그 안에서 {@code HistoryRecorder.start}
     * ({@code REQUIRES_NEW})가 <b>두 번째</b> 커넥션을 요구했다. 기본 풀 10에서 동시 등록 10건이
     * 각자 첫 번째를 쥔 채 두 번째를 기다리면 아무도 진행하지 못한다. 매칭 경로는 UG-293 에서
     * 같은 이유로 트랜잭션을 뗐다.
     *
     * <p>지금은 각 단계가 자기 경계를 연다. 조회는 {@code ApiKeyService} 가(지연 연관도 거기서
     * 초기화한다, UG-335), 시작·실패 이력은 {@code HistoryRecorder} 가, 원격 호출은 어떤
     * 트랜잭션에도 들지 않는다. 한 시점에 쥐는 커넥션은 최대 하나다.
     *
     * <p><b>성공만은 한 트랜잭션으로 묶는다.</b> 특징점 저장과 성공 이력은 원자적이어야 한다 —
     * 따로 커밋하면 "특징점은 없는데 등록 성공 이력만 있는" 상태가 가능해진다(UG-293 반박 리뷰).
     * 그 두 쓰기만 {@link TransactionTemplate} 으로 감싼다. {@code succeed} 는 {@code REQUIRED}
     * 라 그 트랜잭션에 합류한다.
     */
    public CreateFaceFeatureServiceResult createFaceFeature(CallerType callerType,
                                                            Long accountId,
                                                            String apiKey,
                                                            MultipartFile featureImage,
                                                            String description,
                                                            String transactionUuid,
                                                            String externalKey
    ) {
        // UG-281: 검증을 이 메서드 맨 앞에서 한다. 예전에는 호출하는 UseCase 가 등록을
        // 마친 뒤에야 소유를 확인했는데, 이 메서드는 당시 REQUIRES_NEW 라 그 시점엔 이미 특징점과
        // 이력이 별도 트랜잭션으로 커밋된 뒤였다 — 거부해도 남의 갤러리에 얼굴이 남았다.
        ApiKey findApiKey = apiKeyService.findByApiKey(callerType, apiKey, accountId);
        Project project = findApiKey.getProject();

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);
        // UG-336: 원격 호출 전에 한 번만 읽는다. 예전에는 결과를 만들 때 다시 조회했는데,
        // 이 메서드에 트랜잭션이 없으므로 그 조회는 성공 커밋 뒤에 새 커넥션을 요구한다 —
        // 거기서 실패하면 등록은 끝났는데 클라이언트는 500 을 받고, 재시도가 이중 등록이 된다.
        boolean livenessEnabled = projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.FACE, LivenessOperation.REGISTER);
        boolean consentEnabled = findProjectSettings.getConsentEnabled();

        String imagePath = fileService.uploadIfConsent(featureImage, consentEnabled);

        // UG-325/326: 등록은 인증 시도가 아니라 특징점의 생애주기 사건이다 — feature_history 에만 쓴다.
        // (UG-325 의 과도기 이중 기록은 통합 조회가 나가면서 끝났고, V27 이 옛 REGISTER 행을 지웠다.)
        FeatureHistory featureHistory = historyRecorder.start(FeatureHistory.register(
                project, FeatureType.FACE, livenessEnabled, imagePath, transactionUuid,
                consentEnabled));

        var createRequest = new CreateFaceFeignRequestDTO(
                project.getBranchName(),
                featureImage,
                transactionUuid,
                // UG-277 반박 리뷰: 등록은 데모로도 도달하므로 호출자 accountId 를 그대로 보낸다.
                // 데모 DTO 가 0L 을 넘기고, 그 "0" 이 face/palm 이력에서 데모에서 온 행임을
                // 알려주는 유일한 흔적이다. 소유자 id 로 통일하면 데모 등록과 인증 등록이
                // 구분되지 않는다 — 은행권 e-KYC 에서 감사 해상도가 떨어진다.
                // 인증 경로에서는 소유 검증(ENFORCE)이 호출자 == 소유자를 보장하므로 값이 같다.
                String.valueOf(accountId),
                livenessEnabled,
                livenessEnabled);
        String featureId;
        try {
            featureId = faceService.createFace(createRequest);
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
                .type(FeatureType.FACE)
                .featureId(featureId)
                .featureImagePath(imagePath)
                .description(description)
                .isDeleted(false)
                .transactionUuid(transactionUuid)
                .externalKey(normalizeExternalKey(externalKey))
                .build();
        transactionTemplate.executeWithoutResult(status -> {
            biometricFeatureRepository.save(biometricFeature);
            featureHistory.successRegister(biometricFeature);
            historyRecorder.succeed(featureHistory);
        });

        return new CreateFaceFeatureServiceResult(biometricFeature, livenessEnabled, consentEnabled);
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
     *
     * <p>트랜잭션 경계는 {@link #createFaceFeature} 와 같다 (UG-336).
     */
    public BiometricFeature createFaceFeatureByDescriptor(Long accountId,
                                                         String apiKey,
                                                         String descriptor,
                                                         String transactionUuid,
                                                         String externalKey
    ) {
        // descriptor 등록은 인증 경로 전용이다 (데모에 대응 엔드포인트가 없다).
        ApiKey findApiKey = apiKeyService.findOwnedByApiKey(apiKey, accountId);
        Project project = findApiKey.getProject();

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);

        // UG-325/326: 등록은 인증 시도가 아니라 특징점의 생애주기 사건이다 — feature_history 에만 쓴다.
        // (UG-325 의 과도기 이중 기록은 통합 조회가 나가면서 끝났고, V27 이 옛 REGISTER 행을 지웠다.)
        FeatureHistory featureHistory = historyRecorder.start(FeatureHistory.register(
                project, FeatureType.FACE, false, null, transactionUuid,
                findProjectSettings.getConsentEnabled()));

        var createRequest = new CreateFaceByDescriptorFeignRequestDTO(
                project.getBranchName(),
                descriptor,
                transactionUuid,
                // UG-277: 프로젝트 소유자 accountId 를 보낸다. 이 경로는 데모 DTO 가 없어 인증 전용이며,
                // 소유 검증이 호출자 == 소유자를 보장하므로 값이 달라지지 않는다. 호출자 값을 쓰지
                // 않는 이유는 X-Account-Id 가 없을 때 null.toString() 이 되기 때문이다 — 기본
                // 소유 검증이 먼저 거부하므로(Long.equals(null) 은 false) 지금은 도달하지 않는다.
                // 그 한 겹에 기대지 않으려고 여기서도 방어한다 — LOG_ONLY 스위치가 있던 동안에는
                // 실제로 통과했다 (UG-306 에서 제거).
                String.valueOf(project.getAccountId()));
        String featureId;
        try {
            featureId = faceService.createFaceByDescriptor(createRequest);
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
                .type(FeatureType.FACE)
                .featureId(featureId)
                .isDeleted(false)
                .transactionUuid(transactionUuid)
                .externalKey(normalizeExternalKey(externalKey))
                .build();
        transactionTemplate.executeWithoutResult(status -> {
            biometricFeatureRepository.save(biometricFeature);
            featureHistory.successRegister(biometricFeature);
            historyRecorder.succeed(featureHistory);
        });

        return biometricFeature;
    }

    /**
     * UG-333: 외부 키는 고객사 시스템의 사용자 식별자다. 빈 문자열은 "없음" 과 같으므로 null 로 정규화해
     * 저장한다 — 오라클은 '' 를 NULL 로 취급하므로(UG-297) 두 방언에서 같은 값이 남게 하는 뜻도 있다.
     * 프로젝트 안 유일성은 강제하지 않는다 (제품 결정 보류, UG-333).
     */
    public static String normalizeExternalKey(String externalKey) {
        return StringUtils.hasText(externalKey) ? externalKey.trim() : null;
    }

    public BiometricFeature getFaceFeatureByFaceIdAndProjectId(String featureId, Long projectId) {
        return biometricFeatureRepository.findByFeatureIdAndProjectIdAndTypeAndIsDeletedFalse(featureId, projectId, FeatureType.FACE)
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));
    }
}

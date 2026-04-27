package ai.univs.gate.modules.match.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.match.application.input.LivenessInput;
import ai.univs.gate.modules.match.application.result.LivenessResult;
import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.match.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.match.infrastructure.client.dto.LivenessFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.billing.client.BillingClient;
import ai.univs.gate.support.billing.client.dto.BillingDeductFeignRequestDTO;
import ai.univs.gate.support.billing.client.dto.BillingOperationFeignRequestDTO;
import ai.univs.gate.support.face.FaceService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.notify.UseCaseNotifyService;
import ai.univs.gate.support.project.ProjectService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class LivenessUseCase {

    private final MatchHistoryRepository matchHistoryRepository;
    private final ApiKeyService apiKeyService;
    private final ProjectService projectService;
    private final FileService fileService;
    private final FaceService faceService;
    private final BillingClient billingClient;
    private final ProjectSettingsService projectSettingsService;
    private final UseCaseNotifyService useCaseNotifyService;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = CustomFeignException.class
    )
    public LivenessResult execute(LivenessInput input) {
        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();

        // 프로젝트 모듈 타입 'FACE' 확인
        projectService.validateFaceModuleType(project);

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);

        // SDK or Demo 요청인 경우 활성화 체크
        projectSettingsService.checkAvailabilityModules(input.callerType(), findProjectSettings);

        // 사용 가능 여부 확인 (limit 또는 Flex 크레딧)
        billingClient.validate("liveness",
                new BillingOperationFeignRequestDTO(project.getId(), project.getAccountId()));

        // 파일 저장
        var imagePath = fileService.upload(input.matchingFaceImage());

        // 라이브니스 전 단순 이력 저장
        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.LIVENESS)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(true)
                .success(false)
                .matchFaceImagePath(imagePath)
                .transactionUuid(input.transactionUuid())
                .build();
        matchHistoryRepository.save(matchHistory);

        // 라이브니스
        var livenessRequest = new LivenessFeignRequestDTO(
                input.matchingFaceImage(),
                input.transactionUuid(),
                project.getAccountId().toString());
        var data = faceService.liveness(livenessRequest);

        BigDecimal livenessScore = StringUtils.hasText(data.getProbability())
                ? new BigDecimal(data.getProbability())
                : BigDecimal.ZERO;
        if (!data.isSuccess()) {
            matchHistory.fail(livenessScore, data.getPrdioctionDesc().toUpperCase());
        } else {
            // 라이브니스 성공 — 빌링 차감 후 이력 저장
            billingClient.deduct("liveness",
                    new BillingDeductFeignRequestDTO(project.getId(), project.getAccountId()));
            matchHistory.success(livenessScore);
        }

        var result = LivenessResult.from(data, input.transactionUuid());

        // 실패 웹훅 || 알림 전송
        useCaseNotifyService.notify(
                input.callerType(),
                MatchType.LIVENESS.name(),
                matchHistory.getProject().getId(),
                matchHistory.getTransactionUuid(),
                result);

        return result;
    }
}

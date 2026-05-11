package ai.univs.gate.modules.match.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.match.application.input.IdentifyInput;
import ai.univs.gate.modules.match.application.result.IdentifyResult;
import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.match.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.match.infrastructure.client.dto.IdentifyFeignRequestDTO;
import ai.univs.gate.modules.match.infrastructure.client.dto.MatchFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.user.domain.entity.User;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.shared.web.enums.LivenessErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.billing.client.BillingClient;
import ai.univs.gate.support.billing.client.dto.BillingDeductFeignRequestDTO;
import ai.univs.gate.support.billing.client.dto.BillingOperationFeignRequestDTO;
import ai.univs.gate.support.face.FaceService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.notify.UseCaseNotifyService;
import ai.univs.gate.support.project.ProjectService;
import ai.univs.gate.support.project.ProjectSettingsService;
import ai.univs.gate.support.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdentifyUseCase {

    private final MatchHistoryRepository matchHistoryRepository;
    private final ProjectSettingsService projectSettingsService;
    private final ProjectService projectService;
    private final UserService userService;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final FaceService faceService;
    private final BillingClient billingClient;
    private final UseCaseNotifyService useCaseNotifyService;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = CustomFeignException.class
    )
    public IdentifyResult execute(IdentifyInput input) {
        ApiKey findApiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = findApiKey.getProject();

        // 프로젝트 모듈 타입 'FACE' 확인
        projectService.validateFaceModuleType(project);

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);

        // SDK or Demo 요청인 경우 활성화 체크
        projectSettingsService.checkAvailabilityModules(input.callerType(), findProjectSettings);

        // 사용 가능 여부 확인 (limit 또는 Flex 크레딧)
        billingClient.validate("identify",
                new BillingOperationFeignRequestDTO(project.getId(), project.getAccountId()));
        if (findProjectSettings.getLivenessIdentifyingEnabled()) {
            billingClient.validate("liveness",
                    new BillingOperationFeignRequestDTO(project.getId(), project.getAccountId()));
        }

        boolean consentEnabled = Boolean.TRUE.equals(findProjectSettings.getConsentEnabled());

        // 개인정보 동의 시에만 이미지 저장
        var imagePath = consentEnabled ? fileService.upload(input.matchingFaceImage()) : "";

        // 매칭 전 요청 이력 저장
        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.IDENTIFY)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(findProjectSettings.getLivenessIdentifyingEnabled())
                .success(false)
                .matchFaceImagePath(imagePath)
                .transactionUuid(input.transactionUuid())
                .build();
        matchHistoryRepository.save(matchHistory);

        // 1:N 매칭
        var identifyRequest = new IdentifyFeignRequestDTO(
                project.getBranchName(),
                input.matchingFaceImage(),
                input.transactionUuid(),
                input.accountId().toString(),
                findProjectSettings.getLivenessIdentifyingEnabled(), // Liveness
                findProjectSettings.getLivenessIdentifyingEnabled()); // Multi Face

        MatchFeignResponseDTO data;
        try {
            data = faceService.identify(identifyRequest);
        } catch (CustomFeignException e) {
            if (!LivenessErrorType.contains(e.getType())) throw e;

            // 라이브니스 실패 또는 불특정 오류
            matchHistory.fail(BigDecimal.ZERO, e.getType());
            return fail(input.callerType(), matchHistory, consentEnabled);
        }

        // 매칭 실패 정보 저장
        if (!data.isResult()) {
            matchHistory.fail(data.getSimilarity(), ErrorType.NOT_MATCH.name());
            return fail(input.callerType(), matchHistory, consentEnabled);
        }

        // 사용자 조회
        User user;
        try {
            user = userService.getUserByFaceIdAndProjectId(data.getFaceId(), project.getId());
        } catch (CustomGateException e) {
            // 사용자 조회가 안되는 경우
            ErrorType errorType = e.getErrorType();
            matchHistory.fail(BigDecimal.ZERO, errorType.name());
            return fail(input.callerType(), matchHistory, consentEnabled);
        }

        matchHistory.success(user, data.getSimilarity());

        // 매칭 성공 — 빌링 차감 후 이력 저장
        billingClient.deduct("identify",
                new BillingDeductFeignRequestDTO(project.getId(), project.getAccountId()));
        if (findProjectSettings.getLivenessIdentifyingEnabled()) {
            billingClient.deduct("liveness",
                    new BillingDeductFeignRequestDTO(project.getId(), project.getAccountId()));
        }

        return success(input.callerType(), matchHistory, consentEnabled);
    }

    private IdentifyResult fail(CallerType callerType, MatchHistory matchHistory, boolean consentEnabled) {
        String prefixImagePath = fileService.getFileServerPath();
        IdentifyResult failResult = IdentifyResult.failResult(matchHistory, prefixImagePath, consentEnabled);

        // 실패 웹훅 || 알림 전송
        useCaseNotifyService.notify(
                callerType,
                MatchType.IDENTIFY.name(),
                matchHistory.getProject().getId(),
                matchHistory.getTransactionUuid(),
                failResult);
        return failResult;
    }

    private IdentifyResult success(CallerType callerType, MatchHistory matchHistory, boolean consentEnabled) {
        String prefixImagePath = fileService.getFileServerPath();
        IdentifyResult successResult = IdentifyResult.successResult(matchHistory, prefixImagePath, consentEnabled);

        // 성공 웹훅 || 알림 전송
        useCaseNotifyService.notify(
                callerType,
                MatchType.IDENTIFY.name(),
                matchHistory.getProject().getId(),
                matchHistory.getTransactionUuid(),
                successResult);
        return successResult;
    }
}

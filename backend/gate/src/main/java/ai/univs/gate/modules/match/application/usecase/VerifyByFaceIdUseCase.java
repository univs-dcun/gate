package ai.univs.gate.modules.match.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.match.application.input.VerifyByFaceIdInput;
import ai.univs.gate.modules.match.application.result.VerifyByFaceIdResult;
import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.match.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.match.infrastructure.client.dto.MatchFeignResponseDTO;
import ai.univs.gate.modules.match.infrastructure.client.dto.VerifyByFaceIdFeignRequestDTO;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class VerifyByFaceIdUseCase {

    private final MatchHistoryRepository matchHistoryRepository;
    private final FileService fileService;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsService projectSettingsService;
    private final ProjectService projectService;
    private final FaceService faceService;
    private final UserService userService;
    private final BillingClient billingClient;
    private final UseCaseNotifyService useCaseNotifyService;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = CustomFeignException.class
    )
    public VerifyByFaceIdResult execute(VerifyByFaceIdInput input) {
        ApiKey findApiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = findApiKey.getProject();

        // 프로젝트 모듈 타입 'FACE' 확인
        projectService.validateFaceModuleType(project);

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);

        // SDK or Demo 요청인 경우 활성화 체크
        projectSettingsService.checkAvailabilityModules(input.callerType(), findProjectSettings);

        // 사용 가능 여부 확인 (limit 또는 Flex 크레딧)
        billingClient.validate("verify",
                new BillingOperationFeignRequestDTO(project.getId(), project.getAccountId()));
        if (findProjectSettings.getLivenessVerifyingEnabled()) {
            billingClient.validate("liveness",
                    new BillingOperationFeignRequestDTO(project.getId(), project.getAccountId()));
        }

        // 파일 저장
        var imagePath = fileService.upload(input.matchingFaceImage());

        // 1:1 확인 전 요청 이력 저장
        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.VERIFY)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(findProjectSettings.getLivenessVerifyingEnabled())
                .success(false)
                .matchFaceId(input.faceId())
                .matchFaceImagePath(imagePath)
                .transactionUuid(input.transactionUuid())
                .build();
        matchHistoryRepository.save(matchHistory);

        // faceId 사용자 조회
        User user;
        try {
            user = userService.getUserByFaceIdAndProjectId(input.faceId(), project.getId());
        } catch (CustomGateException e) {
            // 사용자 조회가 안되는 경우
            ErrorType errorType = e.getErrorType();
            matchHistory.fail(BigDecimal.ZERO, errorType.name());
            return fail(input.callerType(), matchHistory);
        }

        // 확인 (faceId:이미지)
        var verifyRequest = new VerifyByFaceIdFeignRequestDTO(
                project.getBranchName(),
                user.getFaceId(),
                input.matchingFaceImage(),
                input.transactionUuid(),
                input.accountId().toString(),
                findProjectSettings.getLivenessVerifyingEnabled(), // Liveness
                findProjectSettings.getLivenessVerifyingEnabled()); // Multi face

        MatchFeignResponseDTO data;
        try {
            data = faceService.verifyByFaceId(verifyRequest);
        } catch (CustomFeignException e) {
            if (!LivenessErrorType.contains(e.getType())) throw e;

            // 라이브니스 실패 또는 불특정 오류
            matchHistory.fail(BigDecimal.ZERO, e.getType());
            return fail(input.callerType(), matchHistory);
        }

        // 확인 실패 정보 저장
        if (!data.isResult()) {
            matchHistory.fail(data.getSimilarity(), ErrorType.MISMATCH.name());
            return fail(input.callerType(), matchHistory);
        }

        // 확인 성공 — 빌링 차감 후 이력 저장
        billingClient.deduct("verify",
                new BillingDeductFeignRequestDTO(project.getId(), project.getAccountId()));
        if (findProjectSettings.getLivenessVerifyingEnabled()) {
            billingClient.deduct("liveness",
                    new BillingDeductFeignRequestDTO(project.getId(), project.getAccountId()));
        }

        matchHistory.success(user, data.getSimilarity());
        return success(input.callerType(), matchHistory);
    }

    private VerifyByFaceIdResult fail(CallerType callerType, MatchHistory matchHistory) {
        String prefixImagePath = fileService.getFileServerPath();
        VerifyByFaceIdResult failResult = VerifyByFaceIdResult.failResult(matchHistory, prefixImagePath);

        // 실패 웹훅 || 알림 전송
        useCaseNotifyService.notify(
                callerType,
                MatchType.VERIFY.name(),
                matchHistory.getProject().getId(),
                matchHistory.getTransactionUuid(),
                failResult);
        return failResult;
    }

    private VerifyByFaceIdResult success(CallerType callerType, MatchHistory matchHistory) {
        String prefixImagePath = fileService.getFileServerPath();
        VerifyByFaceIdResult successResult = VerifyByFaceIdResult.successResult(matchHistory, prefixImagePath);

        // 성공 웹훅 || 알림 전송
        useCaseNotifyService.notify(
                callerType,
                MatchType.VERIFY.name(),
                matchHistory.getProject().getId(),
                matchHistory.getTransactionUuid(),
                successResult);
        return successResult;
    }
}

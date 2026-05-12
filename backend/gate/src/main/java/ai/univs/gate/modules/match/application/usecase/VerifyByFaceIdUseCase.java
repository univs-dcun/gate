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
    private final UseCaseNotifyService useCaseNotifyService;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = CustomFeignException.class
    )
    public VerifyByFaceIdResult execute(VerifyByFaceIdInput input) {
        ApiKey findApiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = findApiKey.getProject();

        projectService.validateFaceModuleType(project);

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);

        projectSettingsService.checkAvailabilityModules(input.callerType(), findProjectSettings);

        var imagePath = fileService.upload(input.matchingFaceImage());

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

        User user;
        try {
            user = userService.getUserByFaceIdAndProjectId(input.faceId(), project.getId());
        } catch (CustomGateException e) {
            ErrorType errorType = e.getErrorType();
            matchHistory.fail(BigDecimal.ZERO, errorType.name());
            return fail(input.callerType(), matchHistory);
        }

        var verifyRequest = new VerifyByFaceIdFeignRequestDTO(
                project.getBranchName(),
                user.getFaceId(),
                input.matchingFaceImage(),
                input.transactionUuid(),
                input.accountId().toString(),
                findProjectSettings.getLivenessVerifyingEnabled(),
                findProjectSettings.getLivenessVerifyingEnabled());

        MatchFeignResponseDTO data;
        try {
            data = faceService.verifyByFaceId(verifyRequest);
        } catch (CustomFeignException e) {
            if (!LivenessErrorType.contains(e.getType())) throw e;

            matchHistory.fail(BigDecimal.ZERO, e.getType());
            return fail(input.callerType(), matchHistory);
        }

        if (!data.isResult()) {
            matchHistory.fail(data.getSimilarity(), ErrorType.MISMATCH.name());
            return fail(input.callerType(), matchHistory);
        }

        matchHistory.success(user, data.getSimilarity());
        return success(input.callerType(), matchHistory);
    }

    private VerifyByFaceIdResult fail(CallerType callerType, MatchHistory matchHistory) {
        String prefixImagePath = fileService.getFileServerPath();
        VerifyByFaceIdResult failResult = VerifyByFaceIdResult.failResult(matchHistory, prefixImagePath);

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

        useCaseNotifyService.notify(
                callerType,
                MatchType.VERIFY.name(),
                matchHistory.getProject().getId(),
                matchHistory.getTransactionUuid(),
                successResult);
        return successResult;
    }
}

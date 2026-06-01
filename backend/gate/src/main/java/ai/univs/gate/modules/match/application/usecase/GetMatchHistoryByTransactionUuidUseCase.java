package ai.univs.gate.modules.match.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.match.application.result.MatchHistoryResult;
import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetMatchHistoryByTransactionUuidUseCase {

    private final MatchHistoryRepository matchHistoryRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(readOnly = true)
    public MatchHistoryResult execute(String apiKey, String transactionUuid) {
        ApiKey findApiKey = apiKeyService.findByApiKey(apiKey);
        Project project = findApiKey.getProject();

        MatchHistory matchHistory = matchHistoryRepository.findTopByProjectAndTransactionUuidOrderByCreatedAtDesc(
                        project,
                        transactionUuid)
                .orElseThrow(() -> new CustomGateException(ErrorType.NOT_FOUND_MATCHING_HISTORY));

        ProjectSettings projectSettings = projectSettingsService.findByProject(project);
        return MatchHistoryResult.from(matchHistory, fileService.getFileServerPath(), projectSettings.getConsentEnabled());
    }
}

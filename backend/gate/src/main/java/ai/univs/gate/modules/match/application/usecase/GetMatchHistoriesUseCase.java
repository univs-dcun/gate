package ai.univs.gate.modules.match.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.match.application.result.MatchHistoriesResult;
import ai.univs.gate.modules.match.application.result.MatchHistoryResult;
import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.match.infrastructure.persistence.query.MatchHistoryQuery;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.usecase.result.CustomPageResult;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetMatchHistoriesUseCase {

    private final MatchHistoryRepository matchHistoryRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(readOnly = true)
    public MatchHistoriesResult execute(MatchHistoryQuery query) {
        ApiKey findApiKey = apiKeyService.findByApiKey(query.apiKey());
        var project = findApiKey.getProject();

        ProjectSettings projectSettings = projectSettingsService.findByProject(project);
        boolean consentEnabled = projectSettings.getConsentEnabled();

        long totalCount = matchHistoryRepository.countByProject(project);
        Page<MatchHistory> pagedMatchingHistories = matchHistoryRepository.findAllByQuery(query, project);

        var results = pagedMatchingHistories.getContent().stream()
                .map(matchingHistory -> MatchHistoryResult.from(matchingHistory, fileService.getFileServerPath(), consentEnabled))
                .toList();

        CustomPageResult page = CustomPageResult.from(
                pagedMatchingHistories.getPageable(),
                pagedMatchingHistories.getTotalElements(),
                pagedMatchingHistories.getTotalPages(),
                totalCount);

        return new MatchHistoriesResult(results, page);
    }
}

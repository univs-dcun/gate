package ai.univs.gate.modules.feature.application.usecase.match;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.result.match.MatchHistoriesResult;
import ai.univs.gate.modules.feature.application.result.match.MatchHistoryResult;
import ai.univs.gate.modules.feature.domain.entity.ActivityLog;
import ai.univs.gate.modules.feature.domain.repository.ActivityLogRepository;
import ai.univs.gate.modules.feature.infrastructure.persistence.query.MatchHistoryQuery;
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

    private final ActivityLogRepository activityLogRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(readOnly = true)
    public MatchHistoriesResult execute(MatchHistoryQuery query) {
        ApiKey findApiKey = apiKeyService.findOwnedByApiKey(query.apiKey(), query.accountId());
        var project = findApiKey.getProject();

        ProjectSettings projectSettings = projectSettingsService.findByProject(project);
        boolean consentEnabled = projectSettings.getConsentEnabled();

        // UG-326: match_history ∪ feature_history. 전체 수도 목록과 같은 모집단(includeDeletions)으로 센다.
        long totalCount = activityLogRepository.countByProjectId(project.getId(), query.includeDeletions());
        Page<ActivityLog> pagedMatchingHistories = activityLogRepository.findAllByQuery(query, project.getId());

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

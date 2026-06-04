package ai.univs.gate.modules.palm_media.application.usecase;

import ai.univs.gate.modules.palm_media.application.input.PalmMediaQuery;
import ai.univs.gate.modules.palm_media.application.result.GetPalmMediasResult;
import ai.univs.gate.modules.palm_media.application.result.PalmMediaResult;
import ai.univs.gate.modules.palm_media.domain.entity.PalmMedia;
import ai.univs.gate.modules.palm_media.domain.repository.PalmMediaRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.usecase.result.CustomPageResult;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GetPalmMediasUseCase {

    private final PalmMediaRepository palmMediaRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(readOnly = true)
    public GetPalmMediasResult execute(PalmMediaQuery query) {
        var apiKey = apiKeyService.findByApiKey(query.apiKey());
        Project project = apiKey.getProject();
        ProjectSettings settings = projectSettingsService.findByProject(project);

        long totalCount = palmMediaRepository.countByProjectIdAndIsDeletedFalse(project.getId());
        Page<PalmMedia> page = palmMediaRepository.findAllByQuery(query, project.getId());

        if (page.isEmpty()) {
            return new GetPalmMediasResult(Collections.emptyList(),
                    CustomPageResult.of(org.springframework.data.domain.Page.empty(), totalCount));
        }

        boolean consentEnabled = settings.getConsentEnabled();
        List<PalmMediaResult> contents = page.stream()
                .map(pm -> PalmMediaResult.from(pm, fileService.getFileServerPath(), consentEnabled))
                .toList();

        CustomPageResult pageResult = CustomPageResult.from(
                page.getPageable(),
                page.getTotalElements(),
                page.getTotalPages(),
                totalCount);

        return new GetPalmMediasResult(contents, pageResult);
    }
}

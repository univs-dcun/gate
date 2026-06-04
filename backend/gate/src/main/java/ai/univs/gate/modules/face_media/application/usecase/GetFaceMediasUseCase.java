package ai.univs.gate.modules.face_media.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.face_media.application.input.FaceMediaQuery;
import ai.univs.gate.modules.face_media.application.result.FaceMediaResult;
import ai.univs.gate.modules.face_media.application.result.GetFaceMediasResult;
import ai.univs.gate.modules.face_media.domain.entity.FaceMedia;
import ai.univs.gate.modules.face_media.domain.repository.FaceMediaRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.auth.UserContext;
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
public class GetFaceMediasUseCase {

    private final FaceMediaRepository faceMediaRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(readOnly = true)
    public GetFaceMediasResult execute(FaceMediaQuery query) {
        UserContext userContext = UserContext.get();

        ApiKey apiKey = apiKeyService.findByApiKey(userContext.getApiKey());
        Project project = apiKey.getProject();
        long totalCount = faceMediaRepository.countByProjectIdAndIsDeletedFalse(project.getId());
        Page<FaceMedia> faceMedias = faceMediaRepository.findAllByQuery(query, project.getId());

        if (faceMedias.isEmpty()) {
            return new GetFaceMediasResult(Collections.emptyList(), CustomPageResult.of(Page.empty(), totalCount));
        }

        ProjectSettings projectSettings = projectSettingsService.findByProject(project);
        boolean consentEnabled = projectSettings.getConsentEnabled();
        List<FaceMediaResult> contents = faceMedias.stream()
                .map(fm -> FaceMediaResult.from(fm, fileService.getFileServerPath(), consentEnabled))
                .toList();
        CustomPageResult page = CustomPageResult.from(
                faceMedias.getPageable(),
                faceMedias.getTotalElements(),
                faceMedias.getTotalPages(),
                totalCount);

        return new GetFaceMediasResult(contents, page);
    }
}

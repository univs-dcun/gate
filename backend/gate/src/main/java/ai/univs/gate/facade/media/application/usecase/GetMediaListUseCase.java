package ai.univs.gate.facade.media.application.usecase;

import ai.univs.gate.facade.media.application.input.MediaListQuery;
import ai.univs.gate.facade.media.application.result.MediaItemResult;
import ai.univs.gate.facade.media.application.result.MediaListResult;
import ai.univs.gate.facade.media.infrastructure.persistence.MediaDSLRepository;
import ai.univs.gate.facade.media.infrastructure.persistence.MediaRow;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.usecase.result.CustomPageResult;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GetMediaListUseCase {

    private final MediaDSLRepository mediaDSLRepository;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsService projectSettingsService;
    private final FileService fileService;

    public MediaListResult execute(MediaListQuery query) {
        Project project = apiKeyService.findByApiKey(query.apiKey()).getProject();
        Long projectId = project.getId();
        ProjectSettings settings = projectSettingsService.findByProject(project);
        String imagePrefix = settings.getConsentEnabled() ? fileService.getFileServerPath() : "";

        int offset = (query.page() - 1) * query.pageSize();

        return switch (query.mediaType()) {
            case FACE -> buildSingleResult(
                    mediaDSLRepository.countFace(projectId, query),
                    mediaDSLRepository.findFaceRows(projectId, query, offset, query.pageSize()),
                    query, imagePrefix);
            case PALM -> buildSingleResult(
                    mediaDSLRepository.countPalm(projectId, query),
                    mediaDSLRepository.findPalmRows(projectId, query, offset, query.pageSize()),
                    query, imagePrefix);
            case ALL -> buildAllResult(projectId, query, offset, imagePrefix);
        };
    }

    private MediaListResult buildAllResult(Long projectId, MediaListQuery query, int offset, String imagePrefix) {
        long faceCount = mediaDSLRepository.countFace(projectId, query);
        long palmCount = mediaDSLRepository.countPalm(projectId, query);
        long total = faceCount + palmCount;

        int fetchLimit = offset + query.pageSize();
        List<MediaRow> faceRows = mediaDSLRepository.findFaceRows(projectId, query, 0, fetchLimit);
        List<MediaRow> palmRows = mediaDSLRepository.findPalmRows(projectId, query, 0, fetchLimit);

        List<MediaRow> merged = mergeSortedDesc(faceRows, palmRows);

        int from = Math.min(offset, merged.size());
        int to = Math.min(offset + query.pageSize(), merged.size());
        List<MediaRow> page = merged.subList(from, to);

        return buildResult(page, total, query, imagePrefix);
    }

    private MediaListResult buildSingleResult(long total, List<MediaRow> rows, MediaListQuery query, String imagePrefix) {
        return buildResult(rows, total, query, imagePrefix);
    }

    private MediaListResult buildResult(List<MediaRow> rows, long total, MediaListQuery query, String imagePrefix) {
        List<MediaItemResult> items = rows.stream()
                .map(row -> toItemResult(row, imagePrefix))
                .toList();
        int totalPages = query.pageSize() > 0 ? (int) Math.ceil((double) total / query.pageSize()) : 0;
        CustomPageResult page = new CustomPageResult(query.pageSize(), query.page(), total, totalPages, total);
        return new MediaListResult(items, page);
    }

    private MediaItemResult toItemResult(MediaRow row, String imagePrefix) {
        String imageUrl = StringUtils.hasText(row.imagePath()) && StringUtils.hasText(imagePrefix)
                ? imagePrefix + row.imagePath()
                : "";
        return new MediaItemResult(
                row.mediaType(),
                row.mediaId(),
                row.description(),
                imageUrl,
                row.fid(),
                row.createdAt());
    }

    private List<MediaRow> mergeSortedDesc(List<MediaRow> a, List<MediaRow> b) {
        List<MediaRow> result = new ArrayList<>(a.size() + b.size());
        int i = 0, j = 0;
        while (i < a.size() && j < b.size()) {
            if (!a.get(i).createdAt().isBefore(b.get(j).createdAt())) {
                result.add(a.get(i++));
            } else {
                result.add(b.get(j++));
            }
        }
        while (i < a.size()) result.add(a.get(i++));
        while (j < b.size()) result.add(b.get(j++));
        return result;
    }
}

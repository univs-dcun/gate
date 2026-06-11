package ai.univs.gate.facade.feature.application.usecase;

import ai.univs.gate.facade.feature.application.input.FeatureListQuery;
import ai.univs.gate.facade.feature.application.result.FeatureItemResult;
import ai.univs.gate.facade.feature.application.result.FeatureListResult;
import ai.univs.gate.facade.feature.infrastructure.persistence.FeatureDSLRepository;
import ai.univs.gate.facade.feature.infrastructure.persistence.FeatureRow;
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
public class GetFeatureListUseCase {

    private final FeatureDSLRepository featureDSLRepository;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsService projectSettingsService;
    private final FileService fileService;

    public FeatureListResult execute(FeatureListQuery query) {
        Project project = apiKeyService.findByApiKey(query.apiKey()).getProject();
        Long projectId = project.getId();
        ProjectSettings settings = projectSettingsService.findByProject(project);
        String imagePrefix = settings.getConsentEnabled() ? fileService.getFileServerPath() : "";

        int offset = (query.page() - 1) * query.pageSize();

        return switch (query.featureType()) {
            case FACE -> buildSingleResult(
                    featureDSLRepository.countFace(projectId, query),
                    featureDSLRepository.findFaceRows(projectId, query, offset, query.pageSize()),
                    query, imagePrefix);
            case PALM -> buildSingleResult(
                    featureDSLRepository.countPalm(projectId, query),
                    featureDSLRepository.findPalmRows(projectId, query, offset, query.pageSize()),
                    query, imagePrefix);
            case ALL -> buildAllResult(projectId, query, offset, imagePrefix);
        };
    }

    private FeatureListResult buildAllResult(Long projectId, FeatureListQuery query, int offset, String imagePrefix) {
        long faceCount = featureDSLRepository.countFace(projectId, query);
        long palmCount = featureDSLRepository.countPalm(projectId, query);
        long total = faceCount + palmCount;

        int fetchLimit = offset + query.pageSize();
        List<FeatureRow> faceRows = featureDSLRepository.findFaceRows(projectId, query, 0, fetchLimit);
        List<FeatureRow> palmRows = featureDSLRepository.findPalmRows(projectId, query, 0, fetchLimit);

        List<FeatureRow> merged = mergeSortedDesc(faceRows, palmRows);

        int from = Math.min(offset, merged.size());
        int to = Math.min(offset + query.pageSize(), merged.size());
        List<FeatureRow> page = merged.subList(from, to);

        return buildResult(page, total, query, imagePrefix);
    }

    private FeatureListResult buildSingleResult(long total, List<FeatureRow> rows, FeatureListQuery query, String imagePrefix) {
        return buildResult(rows, total, query, imagePrefix);
    }

    private FeatureListResult buildResult(List<FeatureRow> rows, long total, FeatureListQuery query, String imagePrefix) {
        List<FeatureItemResult> items = rows.stream()
                .map(row -> toItemResult(row, imagePrefix))
                .toList();
        int totalPages = query.pageSize() > 0 ? (int) Math.ceil((double) total / query.pageSize()) : 0;
        CustomPageResult page = new CustomPageResult(query.pageSize(), query.page(), total, totalPages, total);
        return new FeatureListResult(items, page);
    }

    private FeatureItemResult toItemResult(FeatureRow row, String imagePrefix) {
        String imageUrl = StringUtils.hasText(row.imagePath()) && StringUtils.hasText(imagePrefix)
                ? imagePrefix + row.imagePath()
                : "";
        return new FeatureItemResult(
                row.featureType(),
                row.featureSeq(),
                row.description(),
                imageUrl,
                row.featureId(),
                row.createdAt());
    }

    private List<FeatureRow> mergeSortedDesc(List<FeatureRow> a, List<FeatureRow> b) {
        List<FeatureRow> result = new ArrayList<>(a.size() + b.size());
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

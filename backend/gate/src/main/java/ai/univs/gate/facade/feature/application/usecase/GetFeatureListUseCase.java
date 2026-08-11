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

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetFeatureListUseCase {

    private final FeatureDSLRepository featureDSLRepository;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsService projectSettingsService;
    private final FileService fileService;

    public FeatureListResult execute(FeatureListQuery query) {
        Project project = apiKeyService.findOwnedByApiKey(query.apiKey(), query.accountId()).getProject();
        Long projectId = project.getId();
        ProjectSettings settings = projectSettingsService.findByProject(project);
        String prefixImagePath = settings.getConsentEnabled() ? fileService.getFileServerPath() : "";

        int offset = (query.page() - 1) * query.pageSize();

        return switch (query.featureType()) {
            case FACE -> buildResult(
                    featureDSLRepository.findFaceRows(projectId, query, offset, query.pageSize()),
                    featureDSLRepository.countFace(projectId, query),
                    query, prefixImagePath);
            case PALM -> buildResult(
                    featureDSLRepository.findPalmRows(projectId, query, offset, query.pageSize()),
                    featureDSLRepository.countPalm(projectId, query),
                    query, prefixImagePath);
            // UG-269: face·palm 이 같은 테이블이므로 type 조건만 빼면 통합 조회가 된다. 예전에는
            // 양쪽을 offset+pageSize 까지 각각 읽어 자바 힙에서 병합한 뒤 한 페이지만 잘라냈다.
            case ALL -> buildResult(
                    featureDSLRepository.findAllRows(projectId, query, offset, query.pageSize()),
                    featureDSLRepository.countAll(projectId, query),
                    query, prefixImagePath);
        };
    }

    private FeatureListResult buildResult(List<FeatureRow> rows, long total, FeatureListQuery query, String prefixImagePath) {
        List<FeatureItemResult> items = rows.stream()
                .map(row -> toItemResult(row, prefixImagePath))
                .toList();
        int totalPages = query.pageSize() > 0 ? (int) Math.ceil((double) total / query.pageSize()) : 0;
        CustomPageResult page = new CustomPageResult(query.pageSize(), query.page(), total, totalPages, total);
        return new FeatureListResult(items, page);
    }

    private FeatureItemResult toItemResult(FeatureRow row, String prefixImagePath) {
        String imageUrl = StringUtils.hasText(row.imagePath()) && StringUtils.hasText(prefixImagePath)
                ? prefixImagePath + row.imagePath()
                : "";
        return new FeatureItemResult(
                row.featureType().name(),
                row.featureSeq(),
                row.description(),
                imageUrl,
                row.featureId(),
                row.createdAt());
    }
}

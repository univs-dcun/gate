package ai.univs.gate.modules.project.application.result;

import ai.univs.gate.shared.usecase.result.CustomPageResult;

import java.util.List;

public record ProjectsResult(
        List<ProjectSummaryResult> projects,
        CustomPageResult page
) {}

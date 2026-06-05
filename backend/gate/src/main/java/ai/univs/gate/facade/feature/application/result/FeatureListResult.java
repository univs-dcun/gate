package ai.univs.gate.facade.feature.application.result;

import ai.univs.gate.shared.usecase.result.CustomPageResult;

import java.util.List;

public record FeatureListResult(
        List<FeatureItemResult> features,
        CustomPageResult page
) {}

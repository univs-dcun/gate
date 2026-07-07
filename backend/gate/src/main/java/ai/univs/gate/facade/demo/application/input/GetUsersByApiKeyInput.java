package ai.univs.gate.facade.demo.application.input;

import ai.univs.gate.modules.feature.application.input.face.FaceFeatureQuery;

public record GetUsersByApiKeyInput(
        String apiKey,
        String userKeyword,
        int page,
        int pageSize,
        Boolean isDeleted,
        String startDate,
        String endDate,
        String timezone
) {

    public FaceFeatureQuery toFaceFeatureQuery() {
        return new FaceFeatureQuery(
                null,
                apiKey,
                userKeyword,
                page,
                pageSize,
                isDeleted,
                startDate,
                endDate,
                "DESC",
                "faceFeatureId"
        );
    }
}

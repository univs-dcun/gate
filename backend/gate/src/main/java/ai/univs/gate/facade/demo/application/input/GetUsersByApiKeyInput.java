package ai.univs.gate.facade.demo.application.input;

import ai.univs.gate.modules.face_media.application.input.FaceMediaQuery;

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

    public FaceMediaQuery toFaceMediaQuery() {
        return new FaceMediaQuery(
                null,
                apiKey,
                userKeyword,
                page,
                pageSize,
                isDeleted,
                startDate,
                endDate,
                "DESC",
                "faceMediaId"
        );
    }
}

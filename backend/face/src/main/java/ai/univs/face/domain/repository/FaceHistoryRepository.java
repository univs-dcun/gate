package ai.univs.face.domain.repository;

import ai.univs.face.domain.FaceHistory;

public interface FaceHistoryRepository {

    FaceHistory save(FaceHistory faceHistory);
}

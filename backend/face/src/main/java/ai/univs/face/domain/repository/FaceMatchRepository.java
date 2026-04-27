package ai.univs.face.domain.repository;

import ai.univs.face.domain.FaceMatch;

public interface FaceMatchRepository {

    FaceMatch save(FaceMatch faceMatch);
}

package ai.univs.palm.domain.repository;

import ai.univs.palm.domain.PalmHistory;

public interface PalmHistoryRepository {

    PalmHistory save(PalmHistory palmHistory);
}

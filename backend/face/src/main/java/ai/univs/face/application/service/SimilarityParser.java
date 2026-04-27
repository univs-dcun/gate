package ai.univs.face.application.service;

import ai.univs.face.shared.exception.InvalidFaceImageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static ai.univs.face.shared.web.enums.ErrorType.NO_DOUBLE_SIMILARITY;

@Slf4j
@Service
public class SimilarityParser {

    @Value("${face.match.threshold:0.85}")
    private double FACE_MATCH_THRESHOLD;

    public Double parseDoubleSimilarity(String similarity) {
        try {
            return Double.parseDouble(similarity);
        } catch (NumberFormatException e) {
            log.error("Failure to parse to the Double type similarity: {}", similarity);

            throw new InvalidFaceImageException(NO_DOUBLE_SIMILARITY);
        }
    }

    public boolean isMatchingBySimilarity(Double similarity) {
        return similarity >= FACE_MATCH_THRESHOLD;
    }

    public double getThreshold() {
        return FACE_MATCH_THRESHOLD;
    }

    public String getThresholdString() {
        return String.valueOf(FACE_MATCH_THRESHOLD);
    }
}

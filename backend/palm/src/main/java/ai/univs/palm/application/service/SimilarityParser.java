package ai.univs.palm.application.service;

import ai.univs.palm.shared.exception.InvalidPalmImageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static ai.univs.palm.shared.web.enums.ErrorType.NO_DOUBLE_SIMILARITY;

@Slf4j
@Service
public class SimilarityParser {

    @Value("${palm.match.threshold:0.85}")
    private double PALM_MATCH_THRESHOLD;

    public Double parseDoubleSimilarity(String similarity) {
        try {
            return Double.parseDouble(similarity);
        } catch (NumberFormatException e) {
            log.error("Failure to parse to the Double type similarity: {}", similarity);

            throw new InvalidPalmImageException(NO_DOUBLE_SIMILARITY);
        }
    }

    public boolean isMatchingBySimilarity(Double similarity) {
        return similarity >= PALM_MATCH_THRESHOLD;
    }

    public double getThreshold() {
        return PALM_MATCH_THRESHOLD;
    }

    public String getThresholdString() {
        return String.valueOf(PALM_MATCH_THRESHOLD);
    }
}

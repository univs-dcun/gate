package ai.univs.palm.application.result;

public record LivenessResult(
        boolean success,
        double score,
        double threshold,
        String message
) {
}
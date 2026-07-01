package ai.univs.gate.modules.feature.application.input.palm;

public record PalmFeatureQuery(
        Long accountId,
        String apiKey,
        String keyword,
        int page,
        int pageSize,
        Boolean isDeleted,
        String startDate,
        String endDate,
        String direction,
        String sortBy
) {}

package ai.univs.gate.modules.palm_media.api.dto;

import ai.univs.gate.modules.palm_media.application.input.PalmMediaQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springdoc.core.annotations.ParameterObject;

@ParameterObject
public record PalmMediaSelectCondition(
        @Schema(description = "페이지 (1부터 시작)", defaultValue = "1")
        int page,

        @Schema(description = "페이지 크기", defaultValue = "20")
        int pageSize,

        @Schema(description = "검색 키워드 (palmId, description, transactionUuid)")
        String userKeyword,

        @Schema(description = "삭제 여부")
        Boolean isDeleted,

        @Schema(description = "시작일 (yyyy-MM-dd)")
        String startDate,

        @Schema(description = "종료일 (yyyy-MM-dd)")
        String endDate
) {

    public PalmMediaQuery toQuery(Long accountId, String apiKey) {
        return new PalmMediaQuery(
                accountId,
                apiKey,
                userKeyword,
                page,
                pageSize,
                isDeleted,
                startDate,
                endDate,
                "DESC",
                "palmMediaId");
    }
}

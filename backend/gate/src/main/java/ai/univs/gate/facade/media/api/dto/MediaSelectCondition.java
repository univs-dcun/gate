package ai.univs.gate.facade.media.api.dto;

import ai.univs.gate.facade.media.application.input.MediaListQuery;
import ai.univs.gate.facade.media.domain.enums.MediaQueryType;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springdoc.core.annotations.ParameterObject;

@ParameterObject
public record MediaSelectCondition(
        @Schema(description = "미디어 타입 [FACE | PALM | ALL]", defaultValue = "ALL")
        MediaQueryType mediaType,

        @Schema(description = "검색 키워드 (FID 또는 메모)")
        String keyword,

        @Schema(description = "페이지 (1부터 시작)", defaultValue = "1")
        Integer page,

        @Schema(description = "페이지 크기", defaultValue = "10")
        Integer pageSize,

        @Schema(description = "삭제 여부")
        Boolean isDeleted,

        @Schema(description = "시작일 (yyyy-MM-dd)")
        String startDate,

        @Schema(description = "종료일 (yyyy-MM-dd)")
        String endDate
) {

    public MediaListQuery toQuery(Long accountId, String apiKey, String timezone) {
        return new MediaListQuery(
                accountId,
                apiKey,
                mediaType != null ? mediaType : MediaQueryType.ALL,
                keyword,
                page != null ? page : 1,
                pageSize != null ? pageSize : 10,
                isDeleted,
                startDate,
                endDate,
                timezone);
    }
}

package ai.univs.gate.facade.media.api.controller;

import ai.univs.gate.facade.media.api.dto.MediaItemResponse;
import ai.univs.gate.facade.media.api.dto.MediaListResponse;
import ai.univs.gate.facade.media.api.dto.MediaSelectCondition;
import ai.univs.gate.facade.media.application.usecase.GetMediaListUseCase;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.swagger.SwaggerError;
import ai.univs.gate.shared.swagger.SwaggerErrorExample;
import ai.univs.gate.shared.web.dto.CustomPage;
import ai.univs.gate.shared.web.dto.ResponseApi;
import ai.univs.gate.shared.web.enums.ErrorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "특징점 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/media")
public class MediaController {

    private final GetMediaListUseCase getMediaListUseCase;

    @Operation(summary = "특징점 목록 조회 (Face + Palm 통합)")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.API_KEY_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.SETTINGS_NOT_FOUND, status = 400),
    })
    @GetMapping
    public ResponseEntity<ResponseApi<MediaListResponse>> list(
            @ParameterObject @ModelAttribute MediaSelectCondition condition
    ) {
        UserContext ctx = UserContext.get();
        var query = condition.toQuery(ctx.getAccountIdAsLong(), ctx.getApiKey(), ctx.getTimezone());
        var result = getMediaListUseCase.execute(query);

        List<MediaItemResponse> items = result.medias().stream()
                .map(item -> MediaItemResponse.from(item, ctx.getTimezone()))
                .toList();

        var response = new MediaListResponse(items, CustomPage.from(result.page()));
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

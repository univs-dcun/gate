package ai.univs.gate.modules.face_media.api.controller;

import ai.univs.gate.modules.face_media.api.dto.*;
import ai.univs.gate.modules.face_media.application.input.DeleteFaceMediaInput;
import ai.univs.gate.modules.face_media.application.input.GetFaceMediaByFaceIdInput;
import ai.univs.gate.modules.face_media.application.input.GetFaceMediaInput;
import ai.univs.gate.modules.face_media.application.usecase.*;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.swagger.SwaggerError;
import ai.univs.gate.shared.swagger.SwaggerErrorExample;
import ai.univs.gate.shared.web.dto.CustomPage;
import ai.univs.gate.shared.web.dto.ResponseApi;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.webhook.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "사용자")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/users")
public class FaceMediaV1Controller {

    private final CreateFaceMediaUseCase createFaceMediaUseCase;
    private final UpdateFaceMediaUseCase updateFaceMediaUseCase;
    private final DeleteFaceMediaUseCase deleteFaceMediaUseCase;
    private final GetFaceMediaUseCase getFaceMediaUseCase;
    private final GetFaceMediaByFaceIdUseCase getFaceMediaByFaceIdUseCase;
    private final GetFaceMediasUseCase getFaceMediasUseCase;
    private final ApiKeyService apiKeyService;
    private final WebhookService webhookService;

    @Operation(summary = "사용자 등록")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = CreateFaceMediaRequestDTO.class)))
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<FaceMediaResponseDTO>> createUser(
            @ModelAttribute @Valid CreateFaceMediaRequestDTO request
    ) {
        UserContext userContext = UserContext.get();
        var input = request.toInput(userContext.getAccountIdAsLong(), userContext.getApiKey());
        var result = createFaceMediaUseCase.execute(input);
        var response = FaceMediaResponseDTO.from(result, userContext.getTimezone());
        var responseApi = ResponseApi.ok(response);

        Long projectId = apiKeyService.findByApiKey(userContext.getApiKey()).getProject().getId();
        webhookService.send(projectId, "api", "user.register", responseApi);

        return ResponseEntity.ok(responseApi);
    }

    @Operation(summary = "사용자 수정")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = UpdateFaceMediaRequestDTO.class)))
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_USER, status = 400),
    })
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<FaceMediaResponseDTO>> update(
            @ModelAttribute @Valid UpdateFaceMediaRequestDTO request
    ) {
        UserContext userContext = UserContext.get();
        var input = request.toInput(userContext.getAccountIdAsLong(), userContext.getApiKey());
        var result = updateFaceMediaUseCase.execute(input);
        var response = FaceMediaResponseDTO.from(result, userContext.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "userId 기반의 사용자 삭제")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_USER, status = 400),
    })
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = SwaggerDescriptions.USER_ID)
            @PathVariable Long userId
    ) {
        UserContext userContext = UserContext.get();
        var input = new DeleteFaceMediaInput(userContext.getAccountIdAsLong(), userContext.getApiKey(), userId);
        deleteFaceMediaUseCase.execute(input);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "userId 기반의 사용자 조회")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @GetMapping("/{userId}")
    public ResponseEntity<ResponseApi<FaceMediaResponseDTO>> getUser(
            @Parameter(description = SwaggerDescriptions.USER_ID)
            @PathVariable Long userId
    ) {
        UserContext userContext = UserContext.get();
        var input = new GetFaceMediaInput(userContext.getAccountIdAsLong(), userContext.getApiKey(), userId);
        var result = getFaceMediaUseCase.execute(input);
        var response = FaceMediaResponseDTO.from(result, userContext.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "faceId 기반의 사용자 조회")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @GetMapping("/faceId/{faceId}")
    public ResponseEntity<ResponseApi<FaceMediaResponseDTO>> getUserByFaceId(
            @Parameter(description = SwaggerDescriptions.FACE_ID)
            @PathVariable String faceId
    ) {
        UserContext userContext = UserContext.get();
        var input = new GetFaceMediaByFaceIdInput(userContext.getAccountIdAsLong(), userContext.getApiKey(), faceId);
        var result = getFaceMediaByFaceIdUseCase.execute(input);
        var response = FaceMediaResponseDTO.from(result, userContext.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "사용자 목록 조회")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @GetMapping
    public ResponseEntity<ResponseApi<FaceMediasResponseDTO>> getUsers(
            @ParameterObject @ModelAttribute @Valid FaceMediaSelectCondition condition
    ) {
        UserContext userContext = UserContext.get();
        var query = condition.toQuery(userContext.getAccountIdAsLong(), userContext.getApiKey());
        var result = getFaceMediasUseCase.execute(query);

        List<FaceMediaResponseDTO> faceMediaResponses = result.faceMedias().stream()
                .map(fm -> FaceMediaResponseDTO.from(fm, userContext.getTimezone()))
                .toList();

        var page = CustomPage.from(result.page());
        var response = new FaceMediasResponseDTO(faceMediaResponses, page);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

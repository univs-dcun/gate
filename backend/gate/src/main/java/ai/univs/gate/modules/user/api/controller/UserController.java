package ai.univs.gate.modules.user.api.controller;

import ai.univs.gate.modules.user.api.dto.*;
import ai.univs.gate.modules.user.application.input.DeleteUserInput;
import ai.univs.gate.modules.user.application.input.GetUserByFaceId;
import ai.univs.gate.modules.user.application.input.GetUserInput;
import ai.univs.gate.modules.user.application.usecase.*;
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
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final DeleteUserByUserIdUseCase deleteUserByUserIdUseCase;
    private final GetUserUseCase getUserUseCase;
    private final GetUserByFaceIdUseCase getUserByFaceIdUseCase;
    private final GetUsersUseCase getUsersUseCase;
    private final ApiKeyService apiKeyService;
    private final WebhookService webhookService;

    @Operation(summary = "사용자 등록")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<UserResponseDTO>> createUser(
            @ParameterObject @ModelAttribute @Valid CreateUserRequestDTO request
    ) {
        UserContext userContext = UserContext.get();
        var input = request.toCreateUserInput(userContext.getAccountIdAsLong(), userContext.getApiKey());
        var result = createUserUseCase.execute(input);
        var response = UserResponseDTO.from(result, userContext.getTimezone());
        var responseApi = ResponseApi.ok(response);

        Long projectId = apiKeyService.findByApiKey(userContext.getApiKey()).getProject().getId();
        webhookService.send(projectId, "api", "user.register", responseApi);

        return ResponseEntity.ok(responseApi);
    }

    @Operation(summary = "사용자 수정")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
            @SecurityRequirement(name = "X-Api-Key")
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.INVALID_USER, status = 400),
    })
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<UserResponseDTO>> update(
            @ParameterObject @ModelAttribute @Valid UpdateUserRequestDTO request
    ) {
        UserContext userContext = UserContext.get();
        var input = request.toUpdateUserInput(userContext.getAccountIdAsLong(), userContext.getApiKey());
        var result = updateUserUseCase.execute(input);
        var response = UserResponseDTO.from(result, userContext.getTimezone());
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
        var input = new DeleteUserInput(userContext.getAccountIdAsLong(), userContext.getApiKey(), userId);
        deleteUserByUserIdUseCase.execute(input);
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
    public ResponseEntity<ResponseApi<UserResponseDTO>> getUser(
            @Parameter(description = SwaggerDescriptions.USER_ID)
            @PathVariable Long userId
    ) {
        UserContext userContext = UserContext.get();
        var input = new GetUserInput(userContext.getAccountIdAsLong(), userContext.getApiKey(), userId);
        var result = getUserUseCase.execute(input);
        var response = UserResponseDTO.from(result, userContext.getTimezone());
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
    public ResponseEntity<ResponseApi<UserResponseDTO>> getUserByFaceId(
            @Parameter(description = SwaggerDescriptions.FACE_ID)
            @PathVariable String faceId
    ) {
        UserContext userContext = UserContext.get();
        var input = new GetUserByFaceId(userContext.getAccountIdAsLong(), userContext.getApiKey(), faceId);
        var result = getUserByFaceIdUseCase.execute(input);
        var response = UserResponseDTO.from(result, userContext.getTimezone());
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
    public ResponseEntity<ResponseApi<UsersResponseDTO>> getUsers(
            @ParameterObject @ModelAttribute @Valid UserSelectCondition condition
    ) {
        UserContext userContext = UserContext.get();
        var userQuery = condition.toUserQuery(userContext.getAccountIdAsLong(), userContext.getApiKey());
        var result = getUsersUseCase.execute(userQuery);

        List<UserResponseDTO> usersResponse = result.users().stream()
                .map(userResult -> UserResponseDTO.from(userResult, userContext.getTimezone()))
                .toList();

        var page = CustomPage.from(result.page());
        var response = new UsersResponseDTO(usersResponse, page);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

package ai.univs.gate.modules.project.api.controller;

import ai.univs.gate.modules.project.api.dto.*;
import ai.univs.gate.modules.project.application.input.UpdatePackageKeyInput;
import ai.univs.gate.modules.project.application.input.UpdateProjectInput;
import ai.univs.gate.modules.project.application.usecase.*;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.swagger.SwaggerError;
import ai.univs.gate.shared.swagger.SwaggerErrorExample;
import ai.univs.gate.shared.web.dto.CustomPage;
import ai.univs.gate.shared.web.dto.ResponseApi;
import ai.univs.gate.shared.web.enums.ErrorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "프로젝트")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/projects")
public class ProjectController {

    private final CreateProjectUseCase createProjectUseCase;
    private final UpdateProjectUseCase updateProjectUseCase;
    private final UpdatePackageKeyUseCase updatePackageKeyUseCase;
    private final DeleteProjectUseCase deleteProjectUseCase;
    private final GetProjectsUseCase getProjectsUseCase;
    private final GetProjectUseCase getProjectUseCase;

    @Operation(summary = "프로젝트 생성", description = "새로운 프로젝트를 생성합니다 (1계정당 1프로젝트, 추 후 변경 예정)")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.PROJECT_ALREADY_EXISTS, status = 400),
            @SwaggerError(errorType = ErrorType.FREE_PLAN_PROJECT_LIMIT_EXCEEDED, status = 400),
            @SwaggerError(errorType = ErrorType.PLAN_NOT_FOUND, status = 400),
    })
    @PostMapping
    public ResponseEntity<ResponseApi<ProjectResponseDTO>> createProject(
            @RequestBody @Valid CreateProjectRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = request.toCreateProjectInput(ctx.getAccountIdAsLong());
        var result = createProjectUseCase.execute(input);
        var response = ProjectResponseDTO.from(result, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "프로젝트 목록 조회", description = "사용자의 모든 프로젝트 목록을 조회합니다")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @GetMapping
    public ResponseEntity<ResponseApi<ProjectsResponseDTO>> getProjects(
            @ParameterObject @ModelAttribute @Valid SelectProjectCondition condition
    ) {
        UserContext ctx = UserContext.get();
        var input = condition.toProjectQuery(ctx.getAccountIdAsLong());
        var result = getProjectsUseCase.execute(input);

        var contents = result.projects().stream()
                .map(projectResult -> ProjectResponseDTO.from(projectResult, ctx.getTimezone()))
                .toList();
        var page = CustomPage.from(result.page());

        var response = new ProjectsResponseDTO(contents, page);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "프로젝트 상세 조회", description = "특정 프로젝트의 상세 정보를 조회합니다")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 400),
    })
    @GetMapping("/{projectId}")
    public ResponseEntity<ResponseApi<ProjectResponseDTO>> getProject(
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
            @PathVariable Long projectId
    ) {
        UserContext ctx = UserContext.get();
        var result = getProjectUseCase.execute(ctx.getAccountIdAsLong(), projectId);
        var response = ProjectResponseDTO.from(result, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "프로젝트 수정", description = "프로젝트 정보를 수정합니다")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 400),
    })
    @PutMapping("/{projectId}")
    public ResponseEntity<ResponseApi<ProjectResponseDTO>> updateProject(
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = new UpdateProjectInput(
                ctx.getAccountIdAsLong(),
                projectId,
                request.projectName(),
                request.projectDescription(),
                request.projectType());
        var result = updateProjectUseCase.execute(input);
        var response = ProjectResponseDTO.from(result, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "패키지 키 설정", description = "External 타입 프로젝트의 패키지 키를 설정합니다")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 400),
            @SwaggerError(errorType = ErrorType.PACKAGE_KEY_NOT_ALLOWED, status = 400),
    })
    @PatchMapping("/{projectId}/package-key")
    public ResponseEntity<ResponseApi<ProjectResponseDTO>> updatePackageKey(
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
            @PathVariable Long projectId,
            @Valid @RequestBody UpdatePackageKeyRequestDTO request
    ) {
        UserContext ctx = UserContext.get();
        var input = new UpdatePackageKeyInput(
                ctx.getAccountIdAsLong(),
                projectId,
                request.packageKey());
        var result = updatePackageKeyUseCase.execute(input);
        var response = ProjectResponseDTO.from(result, ctx.getTimezone());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "프로젝트 삭제", description = "프로젝트를 삭제합니다 (Soft Delete)")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.PROJECT_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_OWNERSHIP, status = 400),
    })
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(
            @Parameter(description = SwaggerDescriptions.PROJECT_ID)
            @PathVariable Long projectId
    ) {
        UserContext ctx = UserContext.get();
        deleteProjectUseCase.execute(ctx.getAccountIdAsLong(), projectId);
        return ResponseEntity.noContent().build();
    }
}

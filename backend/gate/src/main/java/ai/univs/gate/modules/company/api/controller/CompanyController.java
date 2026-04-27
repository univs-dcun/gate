package ai.univs.gate.modules.company.api.controller;

import ai.univs.gate.modules.company.api.dto.CompanyResponseDTO;
import ai.univs.gate.modules.company.api.dto.InitCompanyRequestDTO;
import ai.univs.gate.modules.company.api.dto.UpsertCompanyRequestDTO;
import ai.univs.gate.modules.company.application.input.UpsertCompanyInput;
import ai.univs.gate.modules.company.application.usecase.GetCompanyUseCase;
import ai.univs.gate.modules.company.application.usecase.UpsertCompanyUseCase;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.swagger.SwaggerError;
import ai.univs.gate.shared.swagger.SwaggerErrorExample;
import ai.univs.gate.shared.web.dto.ResponseApi;
import ai.univs.gate.shared.web.enums.ErrorType;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "기업 정보")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/company")
public class CompanyController {

    private final GetCompanyUseCase getCompanyUseCase;
    private final UpsertCompanyUseCase upsertCompanyUseCase;

    @Operation(summary = "회사 정보 조회", description = "사용자의 회사 정보를 조회합니다")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.COMPANY_NOT_FOUND, status = 400),
    })
    @GetMapping
    public ResponseEntity<ResponseApi<CompanyResponseDTO>> getCompany() {
        UserContext userContext = UserContext.get();
        var result = getCompanyUseCase.execute(userContext.getAccountIdAsLong());
        var response = CompanyResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "회사 정보 수정", description = "회사 정보를 수정합니다.")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PutMapping
    public ResponseEntity<ResponseApi<CompanyResponseDTO>> upsertCompany(
            @Valid @RequestBody UpsertCompanyRequestDTO request
    ) {
        UserContext userContext = UserContext.get();
        var input = new UpsertCompanyInput(
                userContext.getAccountIdAsLong(),
                request.companyName(),
                request.businessNumber(),
                request.managerMail(),
                request.managerName(),
                request.managerNumber(),
                request.mainService(),
                request.businessType(),
                request.employeeCount());

        var result = upsertCompanyUseCase.execute(input);
        var response = CompanyResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Hidden
    @Operation(summary = "회사 정보 등록(초기화)", description = "회원가입 성공시 초기화 진행")
    @SecurityRequirements({
            @SecurityRequirement(name = "Authentication"),
    })
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/internal/init")
    public ResponseEntity<ResponseApi<CompanyResponseDTO>> upsertCompany(@RequestBody InitCompanyRequestDTO request) {
        var input = new UpsertCompanyInput(
                request.accountId(),
                "",
                "",
                request.managerMail(),
                "",
                "",
                "",
                "",
                "");

        var result = upsertCompanyUseCase.execute(input);
        var response = CompanyResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

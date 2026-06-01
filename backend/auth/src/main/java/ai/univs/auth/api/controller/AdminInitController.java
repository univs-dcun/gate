package ai.univs.auth.api.controller;

import ai.univs.auth.api.dto.AdminInitRequestDTO;
import ai.univs.auth.api.dto.AdminInitResponseDTO;
import ai.univs.auth.application.usecase.AdminInitUseCase;
import ai.univs.auth.shared.web.dto.ResponseApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "온프레미스 초기화")
@RestController
@Profile("onpremise")
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/admin")
public class AdminInitController {

    private final AdminInitUseCase adminInitUseCase;

    @Operation(summary = "온프레미스 관리자 계정 초기화 (최초 1회)")
    @PostMapping("/init")
    public ResponseEntity<ResponseApi<AdminInitResponseDTO>> initAdmin(
            @Valid @RequestBody AdminInitRequestDTO request
    ) {
        var result = adminInitUseCase.execute(request.email(), request.password());
        return ResponseEntity.ok(ResponseApi.ok(AdminInitResponseDTO.from(result)));
    }
}

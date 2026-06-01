package ai.univs.palm.api;

import ai.univs.palm.api.dto.*;
import ai.univs.palm.application.usecase.*;
import ai.univs.palm.shared.swagger.SwaggerError;
import ai.univs.palm.shared.swagger.SwaggerErrorExample;
import ai.univs.palm.shared.web.dto.ResponseApi;
import ai.univs.palm.shared.web.enums.ErrorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "팜 매칭 프록시 서버", description = "SmartFace Palm Module 연동 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/palm")
public class PalmController {

    private final RegisterBranchUseCase registerBranchUseCase;
    private final RegisterUseCase registerUseCase;
    private final DeleteUseCase deleteUseCase;
    private final LivenessUseCase livenessUseCase;
    private final IdentifyUseCase identifyUseCase;

    @Operation(summary = "Watchlist(branch) 생성")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping(value = "/branch")
    public ResponseEntity<ResponseApi<BranchResponseDTO>> registerBranch(
            @RequestBody @Valid RegisterBranchRequestDTO request
    ) {
        var result = registerBranchUseCase.execute(request.toInput());
        var response = BranchResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "사용자 팜 등록 - palmId 서버 측에서 관리")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_PALM_IMAGE, status = 400),
            @SwaggerError(errorType = ErrorType.PALM_NOT_FOUND, status = 400),
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<PalmResponseDTO>> registerWithDescriptor(
            @ModelAttribute @Valid RegisterRequestDTO request
    ) {
        var input = request.toV2RegisterInput();
        var result = registerUseCase.execute(input);
        var response = PalmResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "사용자 고유 값으로 사용자 팜 이미지 삭제")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping("/delete")
    public ResponseEntity<ResponseApi<PalmResponseDTO>> delete(
            @RequestBody @Valid DeleteRequestDTO request
    ) {
        var input = request.toDeleteInput();
        var result = deleteUseCase.execute(input);
        var response = PalmResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "라이브니스 검사")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_PALM_IMAGE, status = 400),
            @SwaggerError(errorType = ErrorType.PALM_NOT_FOUND, status = 400),
    })
    @PostMapping(value = "/liveness", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<LivenessResponseDTO>> liveness(
            @ModelAttribute @Valid LivenessRequestDTO request
    ) {
        var input = request.toLivenessInput();
        var result = livenessUseCase.execute(input);
        var response = LivenessResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "1:N 사용자 팜 매칭 결과 반환")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_PALM_IMAGE, status = 400),
            @SwaggerError(errorType = ErrorType.PALM_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_MATCH, status = 400),
    })
    @PostMapping(value = "/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<IdentifyResponseDTO>> identify(
            @ModelAttribute @Valid IdentifyRequestDTO request
    ) {
        var input = request.toIdentifyInput();
        var result = identifyUseCase.execute(input);
        var response = IdentifyResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

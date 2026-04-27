package ai.univs.match.api.controller;

import ai.univs.match.api.dto.*;
import ai.univs.match.application.usecase.*;
import ai.univs.match.shared.swagger.SwaggerError;
import ai.univs.match.shared.swagger.SwaggerErrorExample;
import ai.univs.match.shared.web.dto.ResponseApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static ai.univs.match.shared.web.enums.ErrorType.*;

@Tag(name = "매칭 모듈")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/match")
public class MatcherController {

    private final RegisterWithFaceIdUseCase registerWithFaceIdUseCase;
    private final RegisterUseCase registerUseCase;
    private final UpdateUseCase updateUseCase;
    private final DeleteUseCase deleteUseCase;
    private final VerifyByFaceIdUseCase verifyByFaceIdUseCase;
    private final VerifyByDescriptorUseCase verifyByDescriptorUseCase;
    private final IdentifyUseCase identifyUseCase;

    @Operation(summary = "클라이언트측에서 제공한 faceId 값으로 사용자 특징점 등록")
    @SwaggerErrorExample({
            @SwaggerError(errorType = INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ALREADY_REGISTERED_DESCRIPTOR, status = 400),
    })
    @PostMapping("/face-id")
    public ResponseEntity<ResponseApi<MatchResponseDTO>> registerWithFaceId(
            @RequestBody @Valid RegisterWithFaceIdRequestDTO request
    ) {
        var input = request.toRegisterWithFaceIdInput();
        var result = registerWithFaceIdUseCase.execute(input);
        var response = MatchResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "매처에서 생성한 faceId 값으로 사용자 특징점 등록")
    @SwaggerErrorExample({
            @SwaggerError(errorType = INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ALREADY_REGISTERED_DESCRIPTOR, status = 400),
    })
    @PostMapping
    public ResponseEntity<ResponseApi<MatchResponseDTO>> register(
            @RequestBody @Valid RegisterRequestDTO request
    ) {
        var result = registerUseCase.execute(request.branchName(), request.descriptor());
        var response = MatchResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "매처에 사용자 특징점 수정")
    @SwaggerErrorExample({
            @SwaggerError(errorType = INVALID_INPUT, status = 400),
            @SwaggerError(errorType = EMPTY_GALLERY, status = 400),
            @SwaggerError(errorType = INVALID_FACE_ID, status = 400),
            @SwaggerError(errorType = ALREADY_REGISTERED_DESCRIPTOR, status = 400),
    })
    @PutMapping
    public ResponseEntity<ResponseApi<MatchResponseDTO>> update(
            @RequestBody @Valid UpdateRequestDTO request
    ) {
        var input = request.toUpdateInput();
        var result = updateUseCase.execute(input);
        var response = MatchResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "매처에 사용자 특징점 삭제")
    @SwaggerErrorExample({
            @SwaggerError(errorType = INVALID_INPUT, status = 400),
            @SwaggerError(errorType = EMPTY_GALLERY, status = 400),
            @SwaggerError(errorType = INVALID_FACE_ID, status = 400),
    })
    @PostMapping("/delete")
    public ResponseEntity<ResponseApi<MatchResponseDTO>> delete(
            @RequestBody @Valid DeleteRequestDTO request
    ) {
        var result = deleteUseCase.execute(request.branchName(), request.faceId());
        var response = MatchResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "1:1 매칭 (faceId : 특징점)")
    @SwaggerErrorExample({
            @SwaggerError(errorType = INVALID_INPUT, status = 400),
            @SwaggerError(errorType = EMPTY_GALLERY, status = 400),
            @SwaggerError(errorType = INVALID_FACE_ID, status = 400),
            @SwaggerError(errorType = DIFFERENT_EXTRACTION_TYPE, status = 400),
    })
    @PostMapping("/verify/id")
    public ResponseEntity<ResponseApi<VerifyResponseDTO>> verifyById(
            @RequestBody @Valid VerifyIdRequestDTO request
    ) {
        var input = request.toVerifyByFaceIdInput();
        var result = verifyByFaceIdUseCase.execute(input);
        var response = VerifyResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "1:1 매칭 (특징점 : 대상 특징점)")
    @SwaggerErrorExample({
            @SwaggerError(errorType = INVALID_INPUT, status = 400),
    })
    @PostMapping("/verify/descriptor")
    public ResponseEntity<ResponseApi<VerifyResponseDTO>> verifyByDescriptor(
            @RequestBody @Valid VerifyDescriptorRequestDTO request
    ) {
        var result = verifyByDescriptorUseCase.execute(request.descriptor(), request.targetDescriptor());
        var response = VerifyResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "1:N 매칭")
    @SwaggerErrorExample({
            @SwaggerError(errorType = INVALID_INPUT, status = 400),
            @SwaggerError(errorType = EMPTY_GALLERY, status = 400),
    })
    @PostMapping("/identify")
    public ResponseEntity<ResponseApi<IdentifyResponseDTO>> identify(
            @RequestBody @Valid IdentifyRequestDTO request
    ) {
        var result = identifyUseCase.execute(request.branchName(), request.descriptor());
        var response = IdentifyResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

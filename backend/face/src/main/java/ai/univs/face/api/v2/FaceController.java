package ai.univs.face.api.v2;

import ai.univs.face.api.v1.dto.FaceResponseDTO;
import ai.univs.face.api.v2.dto.*;
import ai.univs.face.application.usecase.ExtractUseCase;
import ai.univs.face.application.usecase.LivenessUseCase;
import ai.univs.face.application.usecase.RegisterUseCase;
import ai.univs.face.shared.swagger.SwaggerError;
import ai.univs.face.shared.swagger.SwaggerErrorExample;
import ai.univs.face.shared.web.dto.ResponseApi;
import ai.univs.face.shared.web.enums.ErrorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "얼굴 매칭 프록시 서버 V2")
@RestController(value = "faceControllerV2")
@RequiredArgsConstructor
@RequestMapping("/api/v2/face")
public class FaceController {

    private final RegisterUseCase registerUseCase;
    private final LivenessUseCase livenessUseCase;
    private final ExtractUseCase extractUseCase;

    @Operation(summary = "사용자 얼굴 등록 V2 - faceId 서버 측에서 관리")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_FACE_IMAGE, status = 400),
            @SwaggerError(errorType = ErrorType.FACE_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.TOO_MANY_FACES, status = 400),
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<FaceResponseDTO>> registerWithDescriptor(
            @ModelAttribute @Valid RegisterRequestDTO request
    ) {
        var input = request.toV2RegisterInput();
        var result = registerUseCase.execute(input);
        var response = FaceResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "라이브니스")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_FACE_IMAGE, status = 400),
            @SwaggerError(errorType = ErrorType.FACE_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.TOO_MANY_FACES, status = 400),
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

    @Operation(summary = "특징점 추출")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_FACE_IMAGE, status = 400),
            @SwaggerError(errorType = ErrorType.FACE_NOT_FOUND, status = 400),
    })
    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<ExtractDescriptorResponseDTO>> extract(
            @ModelAttribute @Valid ExtractRequestDTO request
    ) {
        var input = request.toExtractInput();
        var result = extractUseCase.execute(input);
        var response = new ExtractDescriptorResponseDTO(result.descriptor());
        return ResponseEntity.ok(ResponseApi.ok(response));
    }
}

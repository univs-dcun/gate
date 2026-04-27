package ai.univs.face.api.v1;

import ai.univs.face.api.v1.dto.*;
import ai.univs.face.application.usecase.*;
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
import org.springframework.web.bind.annotation.*;

@Tag(name = "얼굴 매칭 프록시 서버")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/face")
public class FaceController {

    private final RegisterUseCase registerUseCase;
    private final UpdateUseCase updateUseCase;
    private final DeleteUseCase deleteUseCase;
    private final VerifyByIdUseCase verifyByIdUseCase;
    private final VerifyByDescriptorUseCase verifyByDescriptorUseCase;
    private final VerifyByImageUseCase verifyByImageUseCase;
    private final IdentifyUseCase identifyUseCase;

    @Operation(summary = "사용자 얼굴 등록")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_FACE_IMAGE, status = 400),
            @SwaggerError(errorType = ErrorType.FACE_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.TOO_MANY_FACES, status = 400),
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<FaceResponseDTO>> registerWithImage(
            @ModelAttribute @Valid RegisterRequestDTO request
    ) {
        var input = request.toV1RegisterInput();
        var result = registerUseCase.execute(input);
        var response = FaceResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "사용자 얼굴 이미지 교체")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_FACE_IMAGE, status = 400),
            @SwaggerError(errorType = ErrorType.FACE_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.TOO_MANY_FACES, status = 400),
    })
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<FaceResponseDTO>> updateWithImage(
            @ModelAttribute @Valid UpdateRequestDTO request
    ) {
        var input = request.toUpdateInput();
        var result = updateUseCase.execute(input);
        var response =  FaceResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "사용자 고유 값으로 사용자 이미지 삭제")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
    })
    @PostMapping("/delete")
    public ResponseEntity<ResponseApi<FaceResponseDTO>> delete(
           @RequestBody @Valid DeleteRequestDTO request
    ) {
        var input = request.toDeleteInput();
        var result = deleteUseCase.execute(input);
        var response = FaceResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "1:1(faceId) 사용자 얼굴 매칭 결과 반환")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_FACE_IMAGE, status = 400),
            @SwaggerError(errorType = ErrorType.FACE_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.TOO_MANY_FACES, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_MATCH, status = 400),
    })
    @PostMapping(value = "/verify/id", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<VerifyByIdResponseDTO>> verifyById(
            @ModelAttribute @Valid VerifyByIdRequestDTO request
    ) {
        var input = request.toVerifyByIdInput();
        var result = verifyByIdUseCase.execute(input);
        var response = VerifyByIdResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "1:1(image) 사용자 얼굴 매칭 결과 반환")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_FACE_IMAGE, status = 400),
            @SwaggerError(errorType = ErrorType.FACE_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.TOO_MANY_FACES, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_MATCH, status = 400),
    })
    @PostMapping(value = "/verify/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi<VerifyByImageResponseDTO>> verifyByImage(
            @ModelAttribute @Valid VerifyByImageRequestDTO request
    ) {
        var input = request.toVerifyByImageInput();
        var result = verifyByImageUseCase.execute(input);
        var response = VerifyByImageResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "1:1(descriptor) 사용자 얼굴 매칭 결과 반환")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_MATCH, status = 400),
    })
    @PostMapping(value = "/verify/descriptor")
    public ResponseEntity<ResponseApi<VerifyByDescriptorResponseDTO>> verifyByDescriptor(
            @RequestBody @Valid VerifyByDescriptorRequestDTO request
    ) {
        var input = request.toVerifyByDescriptorInput();
        var result = verifyByDescriptorUseCase.execute(input);
        var response = VerifyByDescriptorResponseDTO.from(result);
        return ResponseEntity.ok(ResponseApi.ok(response));
    }

    @Operation(summary = "1:N 사용자 얼굴 매칭 결과 반환")
    @SwaggerErrorExample({
            @SwaggerError(errorType = ErrorType.INVALID_INPUT, status = 400),
            @SwaggerError(errorType = ErrorType.NOT_FACE_IMAGE, status = 400),
            @SwaggerError(errorType = ErrorType.FACE_NOT_FOUND, status = 400),
            @SwaggerError(errorType = ErrorType.TOO_MANY_FACES, status = 400),
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

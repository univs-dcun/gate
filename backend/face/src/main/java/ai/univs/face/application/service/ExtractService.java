package ai.univs.face.application.service;

import ai.univs.face.application.result.ExtractResult;
import ai.univs.face.application.result.LivenessResult;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.FaceLiveness;
import ai.univs.face.infrastructure.feign.extract.ExtractFeign;
import ai.univs.face.infrastructure.feign.extract.dto.ExtractFeignResponseApi;
import ai.univs.face.infrastructure.feign.extract.dto.ExtractFeignResponseDTO;
import ai.univs.face.infrastructure.feign.extract.dto.LivenessBodyFeignResponseDTO;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import ai.univs.face.shared.locale.MessageService;
import ai.univs.face.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static ai.univs.face.shared.web.enums.ErrorType.FACE_NOT_FOUND;
import static ai.univs.face.shared.web.enums.ErrorType.TOO_MANY_FACES;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractService {

    // External Modules
    private final ExtractFeign extractFeign;


    // Common Message
    private final MessageService messageService;


    public ExtractResult extract(FaceHistory faceHistory,
                                 MultipartFile faceImage,
                                 String clientId,
                                 boolean checkLiveness,
                                 boolean checkMultiFace
    ) {
        ExtractFeignResponseApi<ExtractFeignResponseDTO> extractResponse =
                callExtractFeign(faceImage, checkLiveness, checkMultiFace);
        ExtractFeignResponseDTO data = extractResponse.getData();

        validateExtract(faceHistory, extractResponse.getCode(), data.getFaceCount(), clientId);
        if (checkMultiFace) validateMultiFace(faceHistory, data.getFaceCount(), clientId);
        if (checkLiveness) validateLiveness(faceHistory, data.getLiveness(), clientId);

        return new ExtractResult(data.getExtract().getDescriptor());
    }

    public LivenessResult extractForLiveness(FaceHistory faceHistory,
                                             MultipartFile faceImage,
                                             String clientId,
                                             boolean checkLiveness,
                                             boolean checkMultiFace
    ) {
        ExtractFeignResponseApi<ExtractFeignResponseDTO> extractResponse =
                callExtractFeign(faceImage, checkLiveness, checkMultiFace);
        ExtractFeignResponseDTO data = extractResponse.getData();

        validateExtract(faceHistory, extractResponse.getCode(), data.getFaceCount(), clientId);

        if (checkMultiFace && data.getFaceCount() > 1) faceHistory.fail(TOO_MANY_FACES.name(), clientId);

        if (checkLiveness) {
            LivenessBodyFeignResponseDTO livenessResult = data.getLiveness();
            saveLiveness(faceHistory, livenessResult, clientId);

            // prdioction = 0 : 라이브니스 성공
            if (livenessResult.getPrdioction() != 0) {
                faceHistory.fail(livenessResult.getPrdioctionDesc().toUpperCase(), clientId);
            }
        }

        return LivenessResult.from(data.getLiveness());
    }

    // 각 인자 역할, 값 1(true) or 0(false)
    //   face-image: 이미지 반환 여부
    //   image_rotate: 이미지 회전 여부
    //   descriptor: 특징점 반환 여부
    //   liveness: 라이브니스 체크 적용 여부
    //   check_multi_face: 다중 얼굴 체크 적용 여부
    //   image: 특징점 추출을 위한 얼굴 이미지 파일
    private ExtractFeignResponseApi<ExtractFeignResponseDTO> callExtractFeign(MultipartFile image,
                                                                              boolean checkLiveness,
                                                                              boolean checkMultiFace
    ) {
        return extractFeign.extractWithOptionalLivenessAndMultiFace(
                1,
                1,
                1,
                checkLiveness ? 1 : 0,
                checkMultiFace ? 1 : 0,
                image);
    }

    // 특징점 추출 성공 여부
    private void validateExtract(FaceHistory history, String code, int faceCount, String clientId) {
        // code: 성공/실패
        // faceCount: 이미지 파일에서 확인된 얼굴 개수
        if (!"SUCCESS".equalsIgnoreCase(code) || faceCount == 0) {
            ErrorType errorType = ErrorType.FACE_NOT_FOUND;
            fail(history, errorType.getCode(), errorType.name(), clientId);
        }
    }

    // 이미지 파일에서 확인된 얼굴이 1개를 초과하는지 확인
    private void validateMultiFace(FaceHistory history, int faceCount, String clientId) {
        if (faceCount > 1) {
            ErrorType errorType = ErrorType.TOO_MANY_FACES;
            fail(history, errorType.getCode(), errorType.name(), clientId);
        }
    }

    // 라이브니스 성공 여부 체크
    private void validateLiveness(FaceHistory history, LivenessBodyFeignResponseDTO dto, String clientId) {
        saveLiveness(history, dto, clientId);

        // prdioction = 0 : 라이브니스 성공
        // -777 : 라이브니스 실패의 경우 -777 고정 코드 사용
        if (dto.getPrdioction() != 0) {
            // UG-346: 엔진이 준 문자열이라 번들에 없을 수 있다 — 없으면 「라이브니스 검증에 실패하였습니다」.
            String type = dto.getPrdioctionDesc().toUpperCase();
            String message = messageService.getMessageOr(type, "LIVENESS_FAILED");
            history.fail(type, clientId);
            throw new InvalidFaceModuleException("-777", type, message);
        }
    }

    private void saveLiveness(FaceHistory history, LivenessBodyFeignResponseDTO dto, String clientId) {
        FaceLiveness faceLiveness = FaceLiveness.builder()
                .faceHistory(history)
                .probability(dto.getProbability())
                .prdioction(dto.getPrdioction())
                .prdioctionDesc(dto.getPrdioctionDesc())
                .quality(dto.getQuality())
                .threshold(dto.getThreshold() == null ? "" : dto.getThreshold())
                .createdBy(clientId)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .modifiedBy(clientId)
                .modifiedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
        // UG-358 2단계 반박 리뷰 M1: 여기서 저장하지 않는다. 결과 커밋(FaceHistoryRecorder)이 이력과 함께 저장한다.
        history.attachLiveness(faceLiveness);
    }

    private void fail(FaceHistory history, String code, String type, String clientId) {
        String message = messageService.getMessage(type);

        history.fail(type, clientId);

        throw new InvalidFaceModuleException(code, type, message);
    }
}

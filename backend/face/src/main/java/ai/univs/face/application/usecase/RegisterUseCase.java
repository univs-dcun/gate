package ai.univs.face.application.usecase;

import ai.univs.face.application.input.RegisterInput;
import ai.univs.face.application.result.ExtractResult;
import ai.univs.face.application.result.RegisterResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.MatchFeignResponseDTO;
import ai.univs.face.infrastructure.feign.match.dto.RegisterFeignRequestDTO;
import ai.univs.face.infrastructure.feign.match.dto.RegisterV2FeignRequestDTO;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import ai.univs.face.shared.feign.dto.FeignResponseApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class RegisterUseCase {

    private final MatchFeign matchFeign;
    private final FaceHistoryRepository faceHistoryRepository;
    private final ExtractService extractService;

    @Transactional(noRollbackFor = InvalidFaceModuleException.class)
    public RegisterResult execute(RegisterInput input) {
        // 등록 요청 이력 저장
        FaceHistory faceHistory = FaceHistory.create(
                ActionType.ADD,
                input.faceId(),
                input.transactionUuid(),
                input.clientId(),
                input.checkLiveness(),
                input.checkMultiFace());
        faceHistoryRepository.save(faceHistory);

        // 특징점 추출 요청
        ExtractResult extractResult = extractService.extract(
                faceHistory,
                input.faceImage(),
                input.clientId(),
                input.checkLiveness(),
                input.checkMultiFace());

        try {
            // 특징점 등록
            FeignResponseApi<MatchFeignResponseDTO> registerResult;

            // faceId 유/무에 따라 호출되는 매처 등록 API 구분
            if (StringUtils.hasText(input.faceId())) {
                var registerRequest = new RegisterFeignRequestDTO(
                        input.branchName(),
                        input.faceId(),
                        extractResult.descriptor());

                registerResult = matchFeign.registerWithFaceId(registerRequest);
            }
            else {
                var registerRequest = new RegisterV2FeignRequestDTO(input.branchName(), extractResult.descriptor());

                registerResult = matchFeign.register(registerRequest);
            }

            var registerData = registerResult.getData();

            // 등록 성공 이력 저장
            faceHistory.successRegister(true, registerData.getFaceId(), input.clientId());

            return new RegisterResult(
                    registerData.getBranchName(),
                    registerData.getFaceId(),
                    faceHistory.getTransactionUuid());

        } catch (CustomFeignException e) {
            // 등록 실패 이력 저장
            faceHistory.fail(e.getType(), input.clientId());

            throw new InvalidFaceModuleException(
                    e.getCode(),
                    e.getType(),
                    e.getMessage());
        }
    }
}

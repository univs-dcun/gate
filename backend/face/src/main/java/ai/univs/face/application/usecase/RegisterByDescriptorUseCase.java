package ai.univs.face.application.usecase;

import ai.univs.face.application.input.RegisterByDescriptorInput;
import ai.univs.face.application.result.RegisterResult;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.RegisterV2FeignRequestDTO;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * descriptor 기반 얼굴 등록 (UG-279).
 *
 * <p>{@link RegisterUseCase} 와 달리 {@code ExtractService} 를 <b>주입하지 않는다.</b> 이는 의도된
 * 구조로, 라이브니스·다중 얼굴 검사가 실수로라도 실행될 수 없게 만든다. descriptor 를 가지고 있다는
 * 것은 호출자가 이미 추출 단계를 통과했다는 뜻이다. 같은 방식의 선례가
 * {@link VerifyByDescriptorUseCase} 다.
 */
@Component
@RequiredArgsConstructor
public class RegisterByDescriptorUseCase {

    private final MatchFeign matchFeign;
    private final FaceHistoryRepository faceHistoryRepository;

    @Transactional(noRollbackFor = InvalidFaceModuleException.class)
    public RegisterResult execute(RegisterByDescriptorInput input) {
        // 등록 요청 이력 저장 — 검사 대상 이미지가 없으므로 checkLiveness/checkMultiFace 는 false 고정
        FaceHistory faceHistory = FaceHistory.create(
                ActionType.ADD,
                "",
                input.transactionUuid(),
                input.clientId(),
                false,
                false);
        faceHistoryRepository.save(faceHistory);

        try {
            // 특징점 등록 — faceId 는 매처가 발급하므로 register(v2) 경로만 사용한다
            var registerRequest = new RegisterV2FeignRequestDTO(input.branchName(), input.descriptor());
            var registerData = matchFeign.register(registerRequest).getData();

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

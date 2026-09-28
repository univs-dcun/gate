package ai.univs.face.application.usecase;

import ai.univs.face.application.input.RegisterByDescriptorInput;
import ai.univs.face.application.result.RegisterResult;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.RegisterFeignRequestDTO;
import ai.univs.face.infrastructure.feign.match.dto.RegisterV2FeignRequestDTO;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
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
            // 특징점 등록 — 호출자가 id 를 주면 그 id 로, 아니면 매처가 발급한다 (UG-337).
            // 이미지 등록(RegisterUseCase)과 같은 분기다. 매처는 같은 브랜치에 이미 있는 id 를
            // ALREADY_REGISTERED_DESCRIPTOR 로 거절한다 — 덮어쓰지 않는다.
            // 빈 값은 "없음" 이다 — 이미지 등록(RegisterUseCase)과 같은 판정을 쓴다. DTO 가 null 로 바꿔 주지만
            // 그 한 겹에 기대지 않는다(반박 리뷰: 그 한 줄을 빼도 테스트가 초록이었다).
            var registerData = StringUtils.hasText(input.faceId())
                    ? matchFeign.registerWithFaceId(new RegisterFeignRequestDTO(
                            input.branchName(), input.faceId(), input.descriptor())).getData()
                    : matchFeign.register(new RegisterV2FeignRequestDTO(
                            input.branchName(), input.descriptor())).getData();

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

package ai.univs.face.application.usecase;

import ai.univs.face.application.input.RegisterByDescriptorInput;
import ai.univs.face.application.result.RegisterResult;
import ai.univs.face.application.service.FaceHistoryRecorder;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.RegisterFeignRequestDTO;
import ai.univs.face.infrastructure.feign.match.dto.RegisterV2FeignRequestDTO;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * descriptor 기반 얼굴 등록 (UG-279).
 *
 * <p>{@link RegisterUseCase} 와 달리 {@code ExtractService} 를 <b>주입하지 않는다.</b> 이는 의도된
 * 구조로, 라이브니스·다중 얼굴 검사가 실수로라도 실행될 수 없게 만든다. descriptor 를 가지고 있다는
 * 것은 호출자가 이미 추출 단계를 통과했다는 뜻이다. 같은 방식의 선례가
 * {@link VerifyByDescriptorUseCase} 다.
 *
 * <p>트랜잭션이 없다 (UG-358 2단계). match 등록 동안 DB 커넥션을 쥐지 않는다. 결과 커밋이 실패했을 때 이력에
 * faceId 가 남는 이유는 {@link RegisterUseCase#execute} 설명과 같다(UG-338).
 */
@Component
@RequiredArgsConstructor
public class RegisterByDescriptorUseCase {

    private final MatchFeign matchFeign;
    private final FaceHistoryRecorder faceHistoryRecorder;

    public RegisterResult execute(RegisterByDescriptorInput input) {
        // 등록 요청 이력 저장 — 검사 대상 이미지가 없으므로 checkLiveness/checkMultiFace 는 false 고정. 먼저 커밋한다
        FaceHistory faceHistory = faceHistoryRecorder.start(FaceHistory.create(
                ActionType.ADD,
                "",
                input.transactionUuid(),
                input.clientId(),
                false,
                false));

        try {
            return register(input, faceHistory);
        } catch (RuntimeException e) {
            // 어떤 실패든 이력 행을 남긴다 — 예전에는 noRollbackFor 밖의 예외(5xx·타임아웃·match 혼잡)에 행이 롤백됐다
            faceHistoryRecorder.recordFailure(faceHistory, e, input.clientId());
            throw e;
        }
    }

    private RegisterResult register(RegisterByDescriptorInput input, FaceHistory faceHistory) {
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

            // 등록 성공 이력 저장 — faceId 를 먼저 싣고 커밋한다. 커밋이 실패해도 실패 이력에 faceId 가 남는다(UG-338)
            faceHistory.successRegister(true, registerData.getFaceId(), input.clientId());
            faceHistoryRecorder.finish(faceHistory, null);

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

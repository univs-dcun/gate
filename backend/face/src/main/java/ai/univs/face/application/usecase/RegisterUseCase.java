package ai.univs.face.application.usecase;

import ai.univs.face.application.input.RegisterInput;
import ai.univs.face.application.result.ExtractResult;
import ai.univs.face.application.result.RegisterResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.application.service.FaceHistoryRecorder;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.MatchFeignResponseDTO;
import ai.univs.face.infrastructure.feign.match.dto.RegisterFeignRequestDTO;
import ai.univs.face.infrastructure.feign.match.dto.RegisterV2FeignRequestDTO;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import ai.univs.face.shared.feign.dto.FeignResponseApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class RegisterUseCase {

    private final MatchFeign matchFeign;
    private final FaceHistoryRecorder faceHistoryRecorder;
    private final ExtractService extractService;

    /**
     * 트랜잭션이 없다 (UG-358 2단계). 이력 시작·결과만 {@link FaceHistoryRecorder} 가 짧게 커밋하고, fxp 추출과 match
     * 등록 동안에는 DB 커넥션을 쥐지 않는다.
     *
     * <p><b>고아 창(UG-338).</b> match 등록이 성공한 뒤 결과 커밋({@code finish})이 실패하면 match 에는 특징점이 있는데
     * 클라이언트는 오류를 받는다. 예전(한 트랜잭션)에도 같은 창이 있었다 — 커밋이 실패하면 이력까지 롤백돼 흔적이 없었다.
     * 이제는 결과 커밋 <b>전에</b> match 가 준 faceId 를 이력에 실으므로, 실패 이력(result=false, INTERNAL_SERVER_ERROR)에
     * 그 faceId 가 남는다. 호출자 발급 id(UG-337)는 gate 의 {@code OrphanRegistrationReconciler} 가 그 id 로 정리하고,
     * 매처 발급 id 는 예전처럼 정리 수단이 없어 운영자가 이 행(그리고 {@code finish} 의 로그)으로 찾는다.
     */
    public RegisterResult execute(RegisterInput input) {
        // 등록 요청 이력 저장 — 먼저 커밋한다
        FaceHistory faceHistory = faceHistoryRecorder.start(FaceHistory.create(
                ActionType.ADD,
                input.faceId(),
                input.transactionUuid(),
                input.clientId(),
                input.checkLiveness(),
                input.checkMultiFace()));

        try {
            return register(input, faceHistory);
        } catch (RuntimeException e) {
            // 어떤 실패든 이력 행을 남긴다 — 예전에는 noRollbackFor 밖의 예외(5xx·타임아웃·match 혼잡)에 행이 롤백됐다
            faceHistoryRecorder.recordFailure(faceHistory, e, input.clientId());
            throw e;
        }
    }

    private RegisterResult register(RegisterInput input, FaceHistory faceHistory) {
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

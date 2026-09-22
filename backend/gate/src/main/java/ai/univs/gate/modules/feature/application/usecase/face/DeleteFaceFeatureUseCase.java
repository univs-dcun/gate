package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.face.DeleteFaceFeatureInput;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.feature.domain.repository.FeatureHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.DeleteFaceFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.utils.ApiKeyMasker;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.face.FaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteFaceFeatureUseCase {

    private final BiometricFeatureRepository biometricFeatureRepository;
    private final FeatureHistoryRepository featureHistoryRepository;
    private final ApiKeyService apiKeyService;
    private final FaceService faceService;

    /**
     * UG-325: 삭제 이력을 남긴다. 예전에는 face 에 위임하고 {@code is_deleted} 만 켰다 — gate 에는
     * 삭제 기록이 없었고, 대시보드는 그래서 삭제 건수를 "등록됐다가 지금 지워진 것" 으로 우회해
     * 세면서 시점이 틀렸다.
     *
     * <p>{@code noRollbackFor} 가 없으면 face 가 실패할 때 이 트랜잭션이 통째로 롤백되어 이력 행이
     * 사라진다 — 실패한 삭제 시도가 감사에서 더 중요한데 그것만 없어지는 셈이다 (UG-280 과 같은
     * 함정). 실패 시 {@code biometricFeature.delete()} 에는 도달하지 않으므로 대상은 그대로다.
     */
    @Transactional(noRollbackFor = {CustomFeignException.class, RemoteCallException.class})
    public void execute(DeleteFaceFeatureInput input) {
        BiometricFeature biometricFeature = biometricFeatureRepository.findByIdAndTypeAndIsDeletedFalse(input.faceFeatureId(), FeatureType.FACE)
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ApiKey apiKey = apiKeyService.findOwnedByApiKey(input.apiKey(), input.accountId());
        Project project = apiKey.getProject();
        if (!biometricFeature.getProject().equals(project)) {
            log.error("Not faceFeature who created based on this apikey. accountId: {}, apiKey: {}, faceFeatureId: {}",
                    input.accountId(), ApiKeyMasker.mask(input.apiKey()), input.faceFeatureId());
            throw new CustomGateException(ErrorType.INVALID_USER);
        }

        // 소유 검증을 통과한 뒤에 쓴다. 남의 특징점을 지우려던 시도까지 남기면 그 행의 project 가
        // 요청자 것도 대상 것도 아니게 되어 조회 경계가 흐려진다.
        // face_history 와 같은 transaction_uuid 를 쓴다 — 두 서비스의 기록을 이어 볼 수 있어야 한다.
        String transactionUuid = TransactionUtil.useOrCreate(null);
        FeatureHistory featureHistory = featureHistoryRepository.save(
                FeatureHistory.delete(project, biometricFeature, transactionUuid));

        var deleteRequest = new DeleteFaceFeignRequestDTO(
                project.getBranchName(),
                biometricFeature.getFeatureId(),
                transactionUuid,
                // UG-277 반박 리뷰: 인증 전용 경로다 (데모 DTO 없음). String.valueOf(input.accountId()) 는
                // X-Account-Id 가 없으면 문자열 "null" 을 만들고, face/palm 의 StringUtils.hasText
                // 폴백은 자바 null·공백만 걸러 그 "null" 을 그대로 저장한다. 기본 ENFORCE 에서는
                // 소유 검증이 먼저 거부하지만 mode=LOG_ONLY 동안에는 통과한다.
                // 또한 palm 은 수정이 삭제+재등록이라, 재등록이 최초 등록과 다른 값을 쓰면
                // 같은 특징점의 이력이 두 값으로 갈린다.
                String.valueOf(project.getAccountId()));
        try {
            faceService.deleteFace(deleteRequest);
        } catch (CustomFeignException e) {
            featureHistory.fail(e.getType());
            throw e;
        } catch (RemoteCallException e) {
            featureHistory.failUpstream(e);
            throw e;
        }

        biometricFeature.delete();
        featureHistory.successDelete();
    }
}

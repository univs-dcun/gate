package ai.univs.gate.modules.feature.application.usecase.palm;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.palm.DeletePalmFeatureInput;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.feature.domain.repository.FeatureHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.DeletePalmFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.utils.ApiKeyMasker;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.palm.PalmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeletePalmFeatureUseCase {

    private final BiometricFeatureRepository biometricFeatureRepository;
    private final FeatureHistoryRepository featureHistoryRepository;
    private final ApiKeyService apiKeyService;
    private final PalmService palmService;

    /** UG-325: 삭제 이력. 순서와 {@code noRollbackFor} 의 이유는 {@code DeleteFaceFeatureUseCase} 참고. */
    @Transactional(noRollbackFor = {CustomFeignException.class, RemoteCallException.class})
    public void execute(DeletePalmFeatureInput input) {
        BiometricFeature biometricFeature = biometricFeatureRepository.findByIdAndTypeAndIsDeletedFalse(input.palmFeatureId(), FeatureType.PALM)
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ApiKey apiKey = apiKeyService.findOwnedByApiKey(input.apiKey(), input.accountId());
        Project project = apiKey.getProject();
        if (!biometricFeature.getProject().equals(project)) {
            log.error("Not palmFeature who created based on this apikey. accountId: {}, apiKey: {}, palmFeatureId: {}",
                    input.accountId(), ApiKeyMasker.mask(input.apiKey()), input.palmFeatureId());
            throw new CustomGateException(ErrorType.INVALID_USER);
        }

        String transactionUuid = TransactionUtil.useOrCreate(null);
        FeatureHistory featureHistory = featureHistoryRepository.save(
                FeatureHistory.delete(project, biometricFeature, transactionUuid));

        var deleteRequest = new DeletePalmFeignRequestDTO(
                project.getBranchName(),
                biometricFeature.getFeatureId(),
                transactionUuid,
                // UG-277 반박 리뷰: 인증 전용 경로다 (데모 DTO 없음). String.valueOf(input.accountId()) 는
                // X-Account-Id 가 없으면 문자열 "null" 을 만들고, face/palm 의 StringUtils.hasText
                // 폴백은 자바 null·공백만 걸러 그 "null" 을 그대로 저장한다. 기본 ENFORCE 에서는
                // 소유 검증이 먼저 거부하지만 그 한 겹에 기대지 않는다
                // (되돌림 스위치가 있던 동안에는 실제로 통과했다 — UG-306 에서 제거).
                // 또한 palm 은 수정이 삭제+재등록이라, 재등록이 최초 등록과 다른 값을 쓰면
                // 같은 특징점의 이력이 두 값으로 갈린다.
                String.valueOf(project.getAccountId()));
        try {
            palmService.deletePalm(deleteRequest);
        } catch (CustomFeignException e) {
            featureHistory.fail(e.getType());
            throw e;
        } catch (RemoteCallException e) {
            featureHistory.fail(e.getErrorType().name());
            throw e;
        }

        biometricFeature.delete();
        featureHistory.successDelete();
    }
}

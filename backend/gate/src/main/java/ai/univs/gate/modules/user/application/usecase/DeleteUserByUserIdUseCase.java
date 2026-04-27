package ai.univs.gate.modules.user.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.user.application.input.DeleteUserInput;
import ai.univs.gate.modules.user.domain.entity.User;
import ai.univs.gate.modules.user.domain.repository.UserRepository;
import ai.univs.gate.modules.user.infrastructure.client.dto.DeleteFeignRequestDTO;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.billing.client.BillingClient;
import ai.univs.gate.support.billing.client.dto.BillingOperationFeignRequestDTO;
import ai.univs.gate.support.face.FaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteUserByUserIdUseCase {

    private final UserRepository userRepository;
    private final ApiKeyService apiKeyService;
    private final FaceService faceService;
    private final BillingClient billingClient;

    @Transactional
    public void execute(DeleteUserInput input) {
        User user = userRepository.findByIdAndIsDeletedFalse(input.userId())
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();
        if (!user.getProject().equals(project)) {
            log.error("Not user who created based on this apikey. accountId: {}, apyKey: {}, userId: {}",
                    input.accountId(),
                    input.apiKey(),
                    input.userId());
            throw new CustomGateException(ErrorType.INVALID_USER);
        }

        var deleteUserRequest = new DeleteFeignRequestDTO(
                project.getBranchName(),
                user.getFaceId(),
                TransactionUtil.useOrCreate(null),
                String.valueOf(input.accountId()));
        faceService.deleteFace(deleteUserRequest);

        user.delete();

        // 삭제 성공 후 dbUsedCount 감소
        billingClient.decrementDbUsed(
                new BillingOperationFeignRequestDTO(project.getId(), input.accountId()));
    }
}

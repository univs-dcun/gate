package ai.univs.palm.application.usecase;

import ai.univs.palm.application.input.RegisterBranchInput;
import ai.univs.palm.application.result.RegisterBranchResult;
import ai.univs.palm.infrastructure.feign.PalmFeign;
import ai.univs.palm.infrastructure.feign.dto.RegisterBranchFeignRequestDTO;
import ai.univs.palm.infrastructure.feign.dto.RegisterBranchFeignResponseDTO;
import ai.univs.palm.shared.exception.CustomFeignException;
import ai.univs.palm.shared.exception.InvalidPalmModuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterBranchUseCase {

    private final PalmFeign palmFeign;

    public RegisterBranchResult execute(RegisterBranchInput input) {
        try {
            RegisterBranchFeignRequestDTO request = new RegisterBranchFeignRequestDTO(
                    input.displayName(),
                    input.fullName()
            );

            RegisterBranchFeignResponseDTO response = palmFeign.registerBranch(request);

            return new RegisterBranchResult(response.getId());

        } catch (CustomFeignException e) {
            // SmartFace 에러 (409 Conflict: 이미 존재하는 watchlist 등)
            log.warn("Palm module registerBranch failed: [{}] {}", e.getType(), e.getMessage());
            throw new InvalidPalmModuleException(e.getCode(), e.getType(), e.getMessage());
        }
    }
}

package ai.univs.gate.modules.project.api.dto;

import ai.univs.gate.modules.project.application.result.ConsentLogResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ConsentLogResponseDTO(
        @Schema(description = "동의 이력 식별 번호")
        Long id,
        @Schema(description = "프로젝트 식별 번호")
        Long projectId,
        @Schema(description = "변경 계정 식별 번호")
        Long endUserIdentifier,
        @Schema(description = "동의 유형")
        String consentType,
        @Schema(description = "동의 여부")
        Boolean agreed,
        @Schema(description = "요청 IP")
        String ipAddress,
        @Schema(description = "동의 일자")
        LocalDateTime agreedAt,
        @Schema(description = "생성 일자")
        LocalDateTime createdAt
) {
    public static ConsentLogResponseDTO from(ConsentLogResult result) {
        return new ConsentLogResponseDTO(
                result.id(),
                result.projectId(),
                result.endUserIdentifier(),
                result.consentType(),
                result.agreed(),
                result.ipAddress(),
                result.agreedAt(),
                result.createdAt()
        );
    }
}

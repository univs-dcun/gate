package ai.univs.gate.modules.project.application.result;

import ai.univs.gate.modules.project.domain.entity.ConsentLog;

import java.time.LocalDateTime;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

public record ConsentLogResult(
        Long id,
        Long projectId,
        Long endUserIdentifier,
        String consentType,
        Boolean agreed,
        String ipAddress,
        LocalDateTime agreedAt,
        LocalDateTime createdAt
) {
    public static ConsentLogResult from(ConsentLog log, String timezone) {
        return new ConsentLogResult(
                log.getId(),
                log.getProject().getId(),
                log.getEndUserIdentifier(),
                log.getConsentType(),
                log.getAgreed(),
                log.getIpAddress(),
                fromUtc(log.getAgreedAt(), timezone),
                fromUtc(log.getCreatedAt(), timezone)
        );
    }
}

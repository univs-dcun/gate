package ai.univs.gate.modules.palm_media.application.result;

import ai.univs.gate.modules.palm_media.domain.entity.PalmMedia;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

public record PalmMediaResult(
        Long palmMediaId,
        Long projectId,
        String palmId,
        String description,
        String username,
        String palmImagePath,
        LocalDateTime createdAt,
        String transactionUuid,
        Boolean checkLiveness
) {

    public static PalmMediaResult from(PalmMedia palmMedia, String imagePrefix, boolean consentEnabled) {
        return new PalmMediaResult(
                palmMedia.getId(),
                palmMedia.getProject().getId(),
                palmMedia.getPalmId(),
                palmMedia.getDescription(),
                palmMedia.getUsername(),
                consentEnabled && StringUtils.hasText(palmMedia.getPalmImagePath())
                        ? imagePrefix + palmMedia.getPalmImagePath()
                        : "",
                palmMedia.getCreatedAt(),
                palmMedia.getTransactionUuid(),
                null);
    }

    public static PalmMediaResult from(PalmMedia palmMedia, boolean livenessChecked, String imagePrefix, boolean consentEnabled) {
        return new PalmMediaResult(
                palmMedia.getId(),
                palmMedia.getProject().getId(),
                palmMedia.getPalmId(),
                palmMedia.getDescription(),
                palmMedia.getUsername(),
                consentEnabled && StringUtils.hasText(palmMedia.getPalmImagePath())
                        ? imagePrefix + palmMedia.getPalmImagePath()
                        : "",
                palmMedia.getCreatedAt(),
                palmMedia.getTransactionUuid(),
                livenessChecked);
    }
}

package ai.univs.gate.modules.face_media.application.result;

import ai.univs.gate.modules.face_media.domain.entity.FaceMedia;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

public record FaceMediaResult(
        Long faceMediaId,
        Long projectId,
        String faceId,
        String description,
        String username,
        String faceImagePath,
        LocalDateTime createdAt,
        String transactionUuid,
        Boolean checkLiveness
) {

    public static FaceMediaResult from(FaceMedia faceMedia, String imagePrefix, boolean consentEnabled) {
        return new FaceMediaResult(
                faceMedia.getId(),
                faceMedia.getProject().getId(),
                faceMedia.getFaceId(),
                faceMedia.getDescription(),
                faceMedia.getUsername(),
                consentEnabled && StringUtils.hasText(faceMedia.getFaceImagePath())
                        ? imagePrefix + faceMedia.getFaceImagePath()
                        : "",
                faceMedia.getCreatedAt(),
                faceMedia.getTransactionUuid(),
                null);
    }

    public static FaceMediaResult from(FaceMedia faceMedia, boolean livenessChecked, String imagePrefix, boolean consentEnabled) {
        return new FaceMediaResult(
                faceMedia.getId(),
                faceMedia.getProject().getId(),
                faceMedia.getFaceId(),
                faceMedia.getDescription(),
                faceMedia.getUsername(),
                consentEnabled && StringUtils.hasText(faceMedia.getFaceImagePath())
                        ? imagePrefix + faceMedia.getFaceImagePath()
                        : "",
                faceMedia.getCreatedAt(),
                faceMedia.getTransactionUuid(),
                livenessChecked);
    }
}

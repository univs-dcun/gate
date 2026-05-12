package ai.univs.gate.modules.user.application.result;

import ai.univs.gate.modules.user.domain.entity.User;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

public record UserResult(
        Long userId,
        Long projectId,
        String faceId,
        String description,
        String username,
        String faceImagePath,
        LocalDateTime createdAt,
        String transactionUuid,
        Boolean checkLiveness
) {

    public static UserResult from(User user, String imagePrefix) {
        return new UserResult(
                user.getId(),
                user.getProject().getId(),
                user.getFaceId(),
                user.getDescription(),
                user.getUsername(),
                StringUtils.hasText(user.getFaceImagePath()) ? imagePrefix + user.getFaceImagePath() : "",
                user.getCreatedAt(),
                user.getTransactionUuid(),
                null);
    }

    public static UserResult from(User user, boolean livenessChecked, String imagePrefix) {
        return new UserResult(
                user.getId(),
                user.getProject().getId(),
                user.getFaceId(),
                user.getDescription(),
                user.getUsername(),
                StringUtils.hasText(user.getFaceImagePath()) ? imagePrefix + user.getFaceImagePath() : "",
                user.getCreatedAt(),
                user.getTransactionUuid(),
                livenessChecked);
    }
}

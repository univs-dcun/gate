package ai.univs.face.application.input;

import org.springframework.web.multipart.MultipartFile;

public record IdentifyInput(
        String branchName,
        MultipartFile faceImage,
        String transactionUuid,
        String clientId,
        boolean checkLiveness,
        boolean checkMultiFace
) {
}

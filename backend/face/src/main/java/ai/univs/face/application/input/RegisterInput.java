package ai.univs.face.application.input;

import org.springframework.web.multipart.MultipartFile;

public record RegisterInput(
        String faceId,
        MultipartFile faceImage,
        String branchName,
        String transactionUuid,
        String clientId,
        boolean checkLiveness,
        boolean checkMultiFace
) {

}

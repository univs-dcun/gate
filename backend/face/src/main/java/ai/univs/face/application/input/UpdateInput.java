package ai.univs.face.application.input;

import org.springframework.web.multipart.MultipartFile;

public record UpdateInput(
        String branchName,
        String faceId,
        MultipartFile faceImage,
        String transactionUuid,
        String clientId,
        boolean checkLiveness,
        boolean checkMultiFace
) {

}

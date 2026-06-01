package ai.univs.palm.application.input;

import org.springframework.web.multipart.MultipartFile;

public record IdentifyInput(
        String branchName,
        MultipartFile palmImage,
        String transactionUuid,
        String clientId,
        boolean checkLiveness
) {
}

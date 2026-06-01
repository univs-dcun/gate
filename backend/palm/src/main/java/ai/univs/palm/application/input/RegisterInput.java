package ai.univs.palm.application.input;

import org.springframework.web.multipart.MultipartFile;

public record RegisterInput(
        String palmId,
        MultipartFile palmImage,
        String branchName,
        String transactionUuid,
        String clientId,
        boolean checkLiveness
) {

}

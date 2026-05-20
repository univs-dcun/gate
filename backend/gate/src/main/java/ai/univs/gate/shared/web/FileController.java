package ai.univs.gate.shared.web;

import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.file.FileService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/file")
public class FileController {

    private final FileService fileService;

    @GetMapping
    public ResponseEntity<byte[]> getFile(
            @RequestParam String filePath
    ) {
        validateFilePath(filePath);
        byte[] bytes = fileService.down(filePath);
        return ResponseEntity.ok()
                .contentType(resolveMediaType(filePath))
                .body(bytes);
    }

    private void validateFilePath(String filePath) {
        if (!StringUtils.hasText(filePath)
                || filePath.contains("..")
                || !filePath.startsWith("/face/")) {
            throw new CustomGateException(ErrorType.INVALID_FILE_PATH);
        }
    }

    private MediaType resolveMediaType(String filePath) {
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".png"))  return MediaType.IMAGE_PNG;
        if (lower.endsWith(".gif"))  return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}

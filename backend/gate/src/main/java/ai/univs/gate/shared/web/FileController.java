package ai.univs.gate.shared.web;

import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.file.FileAccessGuard;
import ai.univs.gate.support.file.FileService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
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
    private final FileAccessGuard fileAccessGuard;

    @GetMapping
    public ResponseEntity<byte[]> getFile(
            @RequestParam String filePath,
            // UG-386: 짧게 유효한 서명 URL — 응답·웹훅이 주는 사진 URL 에 붙어 온다(FileUrlSigner)
            @RequestParam(name = "pid", required = false) Long projectId,
            @RequestParam(required = false) Long exp,
            @RequestParam(required = false) String sig
    ) {
        validateFilePath(filePath);
        fileAccessGuard.check(filePath, projectId, exp, sig);
        byte[] bytes = fileService.down(filePath);
        // 민감정보(얼굴 · 신분증 사진)라 브라우저 · 중간 서버에 남기지 않는다. 형식 추측(sniffing)도 막는다
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
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

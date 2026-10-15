package ai.univs.gate.support.file;

import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileUtil fileUtil;
    private final FileUrlSigner fileUrlSigner;

    @Value("${file.enable.upload}")
    private boolean FILE_ENABLE_UPLOAD;

    @Value("${gateway.url}")
    private String GATEWAY_URL;

    @Value("${file.api-endpoint.get}")
    private String FILE_GET_API_ENDPOINT;

    public String thumbnail(MultipartFile file) {
        if (!FILE_ENABLE_UPLOAD) return "";

        return fileUtil.fileResizeAndSave(file);
    }

    public String upload(MultipartFile file) {
        if (!FILE_ENABLE_UPLOAD) return "";

        return fileUtil.save(file);
    }

    public String uploadIfConsent(MultipartFile file, boolean consentEnabled) {
        if (!consentEnabled) return null;
        return upload(file);
    }

    public byte[] down(String filePath) {
        validationFilePath(filePath);
        return fileUtil.getFile(filePath);
    }

    public void delete(String filePath) {
        validationFilePath(filePath);
        fileUtil.delete(filePath);
    }

    /** {@link FileUtil#deleteReporting} — 파일이 없을 때 「이미 없음」과 「저장소를 볼 수 없음」을 나눈다 (UG-347). */
    public FileUtil.DeleteOutcome deleteReporting(String filePath) {
        validationFilePath(filePath);
        return fileUtil.deleteReporting(filePath);
    }

    private void validationFilePath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            throw new CustomGateException(ErrorType.INVALID_FILE_PATH);
        }
    }

    /**
     * 이 프로젝트의 응답에 실을 사진 URL 을 만드는 객체 (UG-386) — 동의가 꺼져 있으면 빈 값, 켜져 있으면 짧게 유효한 서명 URL.
     * 예전 {@code getFileServerPath()} + 경로 이어 붙이기를 대신한다.
     */
    public ImageUrls imageUrls(long projectId, boolean consentEnabled) {
        return new ImageUrls(GATEWAY_URL + FILE_GET_API_ENDPOINT, fileUrlSigner, projectId, consentEnabled);
    }
}

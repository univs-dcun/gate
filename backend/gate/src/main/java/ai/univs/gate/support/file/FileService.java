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

    public byte[] down(String filePath) {
        validationFilePath(filePath);
        return fileUtil.getFile(filePath);
    }

    public void delete(String filePath) {
        validationFilePath(filePath);
        fileUtil.delete(filePath);
    }

    private void validationFilePath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            throw new CustomGateException(ErrorType.INVALID_FILE_PATH);
        }
    }

    public String getFileServerPath() {
        return GATEWAY_URL + FILE_GET_API_ENDPOINT + "?filePath=";
    }
}

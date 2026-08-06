package ai.univs.gate.support.file;

import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileUtil {

    @Value("${file.root-path}")
    private String fileRootPath;

    public byte[] getFile(String filePath) {
        try {
            return Files.readAllBytes(Path.of(fileRootPath + filePath));
        } catch (Exception ex) {
            log.error("파일에 접근하지 못했다 — 경로 문제일 수도 있고 볼륨·권한 문제일 수도 있다. filePath={}, rootPath={}", filePath, fileRootPath, ex);
            throw new CustomGateException(ErrorType.INVALID_FILE_PATH);
        }
    }

    public String save(MultipartFile file) {
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String folderPath = createFolder();
        String imagePath = createImagePath(folderPath, extension);
        try (OutputStream out = new FileOutputStream(fileRootPath + imagePath)) {
            out.write(file.getBytes());
            return imagePath;
        } catch (Exception ex) {
            log.error("Write file error: {}", file.getOriginalFilename() + "." + extension, ex);
            throw new CustomGateException(ErrorType.INTERNAL_SERVER_ERROR);
        }
    }

    public void delete(String filePath) {
        try {
            Files.deleteIfExists(Path.of(fileRootPath + filePath));
        } catch (Exception ex) {
            log.error("파일에 접근하지 못했다 — 경로 문제일 수도 있고 볼륨·권한 문제일 수도 있다. filePath={}, rootPath={}", filePath, fileRootPath, ex);
            throw new CustomGateException(ErrorType.INVALID_FILE_PATH);
        }
    }

    // ex) /face/20250106/
    private String createFolder() {
        String yyyyMMdd = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String folderPath = File.separator + "face" + File.separator + yyyyMMdd;

        File folder = new File(fileRootPath + folderPath);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        return folderPath;
    }

    // ex) /face/20250106/OXNOI-HOROI-QN871NQ.jpeg
    private String createImagePath(String folderPath, String extension) {
        // 사용자가 전달한 이미지명은 개인정보로 특정될 수 있으므로 UUID 를 저장합니다.
        return folderPath + File.separator + UUID.randomUUID() + "." + extension;
    }

    public String fileResizeAndSave(MultipartFile file) {
        Image image;
        try (InputStream is = file.getInputStream()) {
            image = ImageIO.read(is);
        } catch (IOException e) {
            // UG-290 반박 리뷰: FAILURE_COMPRESSION_FILE 은 4xx 로 분류돼 있어 핸들러가
            // 스택트레이스를 남기지 않는다. 여기서 남기지 않으면 원인이 완전히 사라진다.
            log.warn("업로드 이미지를 읽지 못했다 — filename={}", file.getOriginalFilename(), e);
            throw new CustomGateException(ErrorType.FAILURE_COMPRESSION_FILE);
        }

        int targetWidth = (int) (image.getWidth(null) * 0.5);
        int targetHeight = (int) (image.getHeight(null) * 0.5);
        String type = file.getContentType().substring(file.getContentType().indexOf("/") + 1);

        try {
            Metadata metadata = getMetadata(file.getInputStream());
            int orientation = getOrientation(metadata);

            BufferedImage bImage = ImageIO.read(file.getInputStream());

            if (orientation != 1) {
                bImage = rotateImage(bImage, orientation);
            }

            BufferedImage bufferedImage = Scalr.resize(bImage, targetWidth, targetHeight);

            String folderPath = createFolder();
            String imagePath = createImagePath(folderPath, type);
            ImageIO.write(bufferedImage, type, new File(fileRootPath + imagePath));
            return imagePath;
        } catch (MetadataException | IOException e) {
            // 같은 ErrorType 이지만 원인이 다르다. 이 블록에는 ImageIO.write 로 디스크에 쓰는
            // 단계가 들어 있어, 디스크 풀·권한 없음·file.root-path 오설정 같은 서버 문제가
            // 여기로 떨어진다 (온프레미스에서 흔하다). 클라이언트 입력 문제와 구분할 단서가
            // 스택트레이스뿐이므로 반드시 남긴다.
            log.error("이미지 리사이즈·저장에 실패했다 — filename={}, rootPath={}",
                    file.getOriginalFilename(), fileRootPath, e);
            throw new CustomGateException(ErrorType.FAILURE_COMPRESSION_FILE);
        }
    }


    private Metadata getMetadata(InputStream inputStream) {
        Metadata metadata;

        try {
            metadata = ImageMetadataReader.readMetadata(inputStream);
        } catch (ImageProcessingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return metadata;
    }

    private Integer getOrientation(Metadata metadata) throws MetadataException {
        int orientation = 1;

        Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);

        if(directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION))  {
            orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
        }

        return orientation;
    }

    private BufferedImage rotateImage (BufferedImage bufferedImage, int orientation) {

        BufferedImage rotatedImage;

        if(orientation == 6 ) {
            rotatedImage = Scalr.rotate(bufferedImage, Scalr.Rotation.CW_90);
        } else if (orientation == 3) {
            rotatedImage = Scalr.rotate(bufferedImage, Scalr.Rotation.CW_180);
        } else if(orientation == 8) {
            rotatedImage = Scalr.rotate(bufferedImage, Scalr.Rotation.CW_270);
        } else {
            rotatedImage = bufferedImage;
        }

        return rotatedImage;
    }
}

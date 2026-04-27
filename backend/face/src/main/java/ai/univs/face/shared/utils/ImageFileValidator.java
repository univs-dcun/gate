package ai.univs.face.shared.utils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.multipart.MultipartFile;

public class ImageFileValidator implements ConstraintValidator<ValidImageFile, MultipartFile> {

    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        return file.getOriginalFilename().toLowerCase().endsWith(".jpg") ||
                file.getOriginalFilename().toLowerCase().endsWith(".jpeg");
    }
}

package ai.univs.gate.shared.utils;

import org.springframework.util.StringUtils;

public class ImagePathUtil {

    public static String get(boolean consentEnabled, String prefixImagePath, String imagePath) {
        return consentEnabled && StringUtils.hasText(imagePath)
                ? prefixImagePath + imagePath
                : ""
                ;
    }
}

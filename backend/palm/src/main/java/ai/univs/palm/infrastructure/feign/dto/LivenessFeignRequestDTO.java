package ai.univs.palm.infrastructure.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LivenessFeignRequestDTO {

    private ImageDTO image;
    private String palmDetectorResourceId;
    private List<String> spoofDetectorResourceIds;
    private SpoofCheckConfigDTO spoofCheckConfig;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageDTO {
        private String data; // Base64 encoded image
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpoofCheckConfigDTO {
        private int livenessScoreThreshold;
    }
}

package ai.univs.palm.infrastructure.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LivenessFeignResponseDTO {
    private boolean performed;
    private boolean passed;
    private LivenessSpoofCheckDTO livenessSpoofCheck;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LivenessSpoofCheckDTO {
        private boolean performed;
        private boolean passed;
        private double score;
    }
}

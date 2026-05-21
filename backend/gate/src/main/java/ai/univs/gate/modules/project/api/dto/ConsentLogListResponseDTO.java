package ai.univs.gate.modules.project.api.dto;

import java.util.List;

public record ConsentLogListResponseDTO(
        List<ConsentLogResponseDTO> contents
) {
    public static ConsentLogListResponseDTO of(List<ConsentLogResponseDTO> contents) {
        return new ConsentLogListResponseDTO(contents);
    }
}

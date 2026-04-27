package ai.univs.gate.modules.project.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProjectModuleType {

    FACE("얼굴 인식"),
    PALM("손바닥 인식");

    private final String description;
}

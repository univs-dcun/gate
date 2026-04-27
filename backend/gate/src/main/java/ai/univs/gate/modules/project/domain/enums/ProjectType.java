package ai.univs.gate.modules.project.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProjectType {

    STANDARD("스탠다드"),
    EXTERNAL("외부 연동");

    private final String description;
}

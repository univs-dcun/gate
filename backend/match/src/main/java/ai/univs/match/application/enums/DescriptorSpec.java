package ai.univs.match.application.enums;

import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import lombok.Getter;

@Getter
public enum DescriptorSpec {

    VERSION_59(59, 15.82862185F, -18.53282296F),
    VERSION_60(60, 15.8779453F, -18.82809308F),
    VERSION_62(62, 15.56840403F, -18.32415094F);

    private final int version;
    private final float descriptorPlattSacleingA;
    private final float descriptorPlattSacleingB;

    DescriptorSpec(int version, float descriptorPlattSacleingA, float descriptorPlattSacleingB) {
        this.version = version;
        this.descriptorPlattSacleingA = descriptorPlattSacleingA;
        this.descriptorPlattSacleingB = descriptorPlattSacleingB;
    }

    public static DescriptorSpec fromVersion(int version) {
        for (DescriptorSpec descriptorSpec : values()) {
            if (descriptorSpec.getVersion() == version) {
                return descriptorSpec;
            }
        }

        throw new CustomFaceMatcherException(ErrorType.NOT_SUPPORTED_VERSION);
    }
}

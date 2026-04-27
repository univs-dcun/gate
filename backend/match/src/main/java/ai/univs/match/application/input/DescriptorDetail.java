package ai.univs.match.application.input;

import ai.univs.match.application.enums.DescriptorSpec;

import java.util.Arrays;
import java.util.Base64;

import static ai.univs.match.shared.utils.SimilarityCalculator.getSimilarityTypeByVersionOfDescriptor;

public record DescriptorDetail(
        byte[] descriptor,
        byte[] descriptorType,
        byte[] descriptorBody,
        DescriptorSpec descriptorSpec
) {

    public static DescriptorDetail from(String base64Descriptor) {
        byte[] legacy = Base64.getDecoder().decode(base64Descriptor);

        int descriptorTypeStartIndex = 0;
        int descriptorTypeEndIndex = 8;

        return new DescriptorDetail(
                Arrays.copyOf(legacy, legacy.length),
                Arrays.copyOfRange(legacy, descriptorTypeStartIndex, descriptorTypeEndIndex),
                Arrays.copyOfRange(legacy, descriptorTypeEndIndex, legacy.length),
                getSimilarityTypeByVersionOfDescriptor(legacy));
    }
}

package ai.univs.gate.shared.web.enums;

public enum LivenessErrorType {

    FACE_NOT_FOUND,
    TOO_MANY_FACES,
    SMALL_FACE_SIZE,
    SMALL_RELATIVE_FACE_SIZE,
    SMALL_PUPILLARY_DISTANCE,
    LARGE_FACE_ROTATION_ANGLE,
    FACE_TOO_CLOSE,
    FACE_CLOSE_TO_BORDER,
    FACE_CROPPED,
    FACE_OCCLUDED,
    EYES_CLOSED,
    DARK_IMAGE,
    FAKE,
    FACE_IS_OCCLUDED,
    FACE_TOO_SMALL,
    FACE_ANGLE_TOO_LARGE,
    ;

    public static boolean contains(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        for (LivenessErrorType type : LivenessErrorType.values()) {
            if (type.name().equals(value)) {
                return true;
            }
        }

        return false;
    }
}

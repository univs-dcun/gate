package ai.univs.gate.shared.web.enums;

import ai.univs.gate.shared.exception.CustomFeignException;

/**
 * 얼굴 추출 단계(얼굴 검출 · 품질 · 라이브니스 판정)에서 거절된 사유 — 1:1 · 1:N 은 이 거절을 오류가 아니라 200
 * {@code success=false} 로 돌려준다.
 *
 * <p>UG-391: 예전에는 아래 목록에 있는 사유만 흡수했다. 엔진이 목록에 없는 사유(예: {@code LIVENESS_NOT_PASSED})를 주면
 * 같은 「촬영 실패」인데도 400 {@code -777} 오류로 나갔다. 이제 face 의 엔진 판정 오류 코드({@link #ENGINE_REJECT_CODE})면
 * 사유 문자열과 관계없이 흡수한다. 목록은 FACE-007 · FACE-008 처럼 엔진 코드가 아닌 사유를 위해 남긴다.
 */
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

    /** face 가 엔진 품질 · 라이브니스 판정 거절에 붙이는 오류 코드 — 사유는 errors.type 에 온다 (face ExtractService) */
    public static final String ENGINE_REJECT_CODE = "-777";

    /** 엔진이 사유 없이 거절했을 때 이력 · 응답에 남길 값 — 라이브니스 API 와 같다 */
    public static final String UNSPECIFIED_REJECT = "LIVENESS_FAILED";

    /** 이 하위 오류를 200 {@code success=false} 로 흡수하는가 (UG-391) */
    public static boolean absorbs(CustomFeignException e) {
        return contains(e.getType()) || ENGINE_REJECT_CODE.equals(e.getCode());
    }

    /** 이력 · 응답에 남길 실패 타입. 엔진 거절인데 사유가 비었으면 {@link #UNSPECIFIED_REJECT} */
    public static String failureTypeOf(CustomFeignException e) {
        String type = e.getType();
        if ((type == null || type.isBlank()) && ENGINE_REJECT_CODE.equals(e.getCode())) {
            return UNSPECIFIED_REJECT;
        }
        return type;
    }

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

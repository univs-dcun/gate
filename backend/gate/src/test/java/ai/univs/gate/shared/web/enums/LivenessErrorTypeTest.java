package ai.univs.gate.shared.web.enums;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.shared.exception.CustomFeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LivenessErrorType — 1:1 · 1:N 이 200 실패로 흡수할 하위 오류 (UG-391)")
class LivenessErrorTypeTest {

    @Test
    @DisplayName("목록에 있는 사유는 코드와 상관없이 흡수한다 (FACE-008 FACE_NOT_FOUND 등)")
    void listedReason() {
        assertThat(LivenessErrorType.absorbs(new CustomFeignException("FACE-008", "FACE_NOT_FOUND", "m"))).isTrue();
        assertThat(LivenessErrorType.absorbs(new CustomFeignException("-777", "FACE_CLOSE_TO_BORDER", "m"))).isTrue();
    }

    @Test
    @DisplayName("엔진 판정 거절(-777)은 목록에 없는 사유도 흡수한다")
    void engineRejectUnlisted() {
        assertThat(LivenessErrorType.absorbs(new CustomFeignException("-777", "LIVENESS_NOT_PASSED", "m"))).isTrue();
        assertThat(LivenessErrorType.absorbs(new CustomFeignException("-777", "", "m"))).isTrue();
    }

    @Test
    @DisplayName("엔진 거절이 아니고 목록에도 없으면 흡수하지 않는다 (MATCH · 내부 오류)")
    void notAbsorbed() {
        assertThat(LivenessErrorType.absorbs(new CustomFeignException("MATCH-001", "EMPTY_GALLERY", "m"))).isFalse();
        assertThat(LivenessErrorType.absorbs(new CustomFeignException("ML-500", "INTERNAL_ERROR", "m"))).isFalse();
        assertThat(LivenessErrorType.absorbs(new CustomFeignException(null, null, "m"))).isFalse();
    }

    @Test
    @DisplayName("이력에 남길 타입: 엔진 거절인데 사유가 비면 LIVENESS_FAILED, 그 밖은 받은 그대로")
    void failureTypeOf() {
        assertThat(LivenessErrorType.failureTypeOf(new CustomFeignException("-777", "", "m"))).isEqualTo("LIVENESS_FAILED");
        assertThat(LivenessErrorType.failureTypeOf(new CustomFeignException("-777", null, "m"))).isEqualTo("LIVENESS_FAILED");
        assertThat(LivenessErrorType.failureTypeOf(new CustomFeignException("-777", "FAKE", "m"))).isEqualTo("FAKE");
        assertThat(LivenessErrorType.failureTypeOf(new CustomFeignException("MATCH-001", "EMPTY_GALLERY", "m"))).isEqualTo("EMPTY_GALLERY");
    }
}

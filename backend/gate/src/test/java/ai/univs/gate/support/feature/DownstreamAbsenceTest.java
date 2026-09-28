package ai.univs.gate.support.feature;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.shared.exception.CustomFeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 삭제 응답에서 "이미 없다" 로 볼 유형 (UG-338).
 *
 * <p>없음을 실패로 세면 삭제 재시도·퍼지·정리 잡이 수렴하지 않는다. 반대로 다른 오류를 없음으로 세면
 * 하위에 남은 특징점을 지운 것으로 착각한다 — 그래서 목록 밖은 전부 실패여야 한다.
 */
@DisplayName("UG-338: 하위의 '없음' 판정")
class DownstreamAbsenceTest {

    @ParameterizedTest(name = "{0}/{1} → 이미_없다={2}, 등록이_닿지_않았다={3}")
    @CsvSource({
            "MATCH-004, INVALID_FACE_ID, true, true",
            // 브랜치가 없다 — 정리 잡에서만 없음이다 (2차 반박 리뷰). 성공 등록된 특징점의 삭제·퍼지에서는
            // match 데이터 유실이나 설정 오류를 뜻하므로 실패로 드러나야 한다.
            "MATCH-001, EMPTY_GALLERY, false, true",
            "SWAGGER-005, INTERNAL_SERVER_ERROR, false, false",
            "FACE-404, FACE_NOT_FOUND, false, false",
            "PALM-008, PALM_NOT_FOUND, false, false",
    })
    void 판정(String code, String type, boolean 없다, boolean 닿지_않았다) {
        CustomFeignException e = new CustomFeignException(code, type, "m");
        assertThat(DownstreamAbsence.이미_없다(e)).isEqualTo(없다);
        assertThat(DownstreamAbsence.등록이_닿지_않았다(e)).isEqualTo(닿지_않았다);
    }
}

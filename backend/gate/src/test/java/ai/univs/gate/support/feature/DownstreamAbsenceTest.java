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

    @ParameterizedTest(name = "{0}/{1} → {2}")
    @CsvSource({
            "MATCH-004, INVALID_FACE_ID, true",
            // 브랜치가 없다 — 첫 등록이 match 에 닿지 않은 새 프로젝트 (반박 리뷰)
            "MATCH-001, EMPTY_GALLERY, true",
            "SWAGGER-005, INTERNAL_SERVER_ERROR, false",
            "FACE-404, FACE_NOT_FOUND, false",
            "PALM-008, PALM_NOT_FOUND, false",
    })
    void 판정(String code, String type, boolean 없다) {
        assertThat(DownstreamAbsence.이미_없다(new CustomFeignException(code, type, "m"))).isEqualTo(없다);
    }
}

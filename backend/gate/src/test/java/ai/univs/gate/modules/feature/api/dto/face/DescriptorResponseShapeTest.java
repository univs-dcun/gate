package ai.univs.gate.modules.feature.api.dto.face;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UG-283: descriptor 1:1 응답과 1:N 응답의 필드 구성이 어긋나지 않아야 한다.
 *
 * <p>두 DTO 를 하나로 합치지 않고 분리해 둔 것은 의도다 — 향후 1:1 에서 {@code featureId} 를 빼는
 * 등 독립적으로 진화할 수 있어야 한다. 다만 분리해 두면 한쪽만 고쳐서 조용히 어긋날 수 있다.
 * 클라이언트가 두 API 를 같은 파서로 다루는 것이 이 티켓의 목적이므로, 어긋나는 순간 실패하게 한다.
 *
 * <p>의도적으로 갈라놓을 때는 이 테스트를 함께 고치면 된다. 그때 "왜 갈라지는가" 를 적는 것이
 * 이 테스트의 진짜 목적이다.
 */
@DisplayName("UG-283: descriptor 1:1 · 1:N 응답 구조 동기화")
class DescriptorResponseShapeTest {

    private static List<String> signature(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(c -> c.getType().getSimpleName() + " " + c.getName())
                .toList();
    }

    @Test
    @DisplayName("1:1 과 1:N 의 필드 이름·타입·순서가 완전히 같다")
    void 구조_일치() {
        assertThat(signature(VerifyByDescriptorResponseDTO.class))
                .as("한쪽만 바꾸면 클라이언트가 두 API 를 같은 파서로 다룰 수 없게 된다. "
                        + "의도적으로 갈라놓는 것이라면 이 테스트도 함께 고치고 사유를 남길 것")
                .isEqualTo(signature(IdentifyByDescriptorResponseDTO.class));
    }

    @Test
    @DisplayName("합의된 필드 집합에서 벗어나지 않는다")
    void 필드_목록_고정() {
        // 값이 아니라 '무엇을 공표하기로 했는지' 를 못 박는다. 필드를 추가·제거하려면
        // 클라이언트 계약이 바뀌는 것이므로 문서(gate-api-docs.html)·openapi 기준선과
        // 함께 갱신해야 한다.
        List<String> expected = List.of(
                "Long matchingHistoryId",
                "Long projectId",
                "MatchType matchType",
                "LocalDateTime matchingTime",
                "Boolean success",
                "String featureId",
                "BigDecimal similarity",
                "String failureType",
                "String failureReason",
                "String transactionUuid");

        assertThat(signature(VerifyByDescriptorResponseDTO.class)).isEqualTo(expected);
        assertThat(signature(IdentifyByDescriptorResponseDTO.class)).isEqualTo(expected);
    }

    @Test
    @DisplayName("이전 3필드 구조의 잔재가 남아 있지 않다")
    void 구조_회귀() {
        List<String> names = Arrays.stream(VerifyByDescriptorResponseDTO.class.getRecordComponents())
                .map(RecordComponent::getName).toList();

        assertThat(names)
                .as("result 는 success 로, 문자열 similarity 는 BigDecimal 백분율로 바뀌었다")
                .doesNotContain("result");
    }
}

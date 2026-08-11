package ai.univs.match.shared.utils;

import ai.univs.match.application.enums.DescriptorSpec;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UG-314 가 딛고 선 전제를 지킨다 — <b>거리 상위 k건 = 유사도 상위 k건</b>.
 *
 * <p>후보 목록 API 는 임계치를 SQL 로 내리지 않는다. 거리로 상위 k건만 뽑아 온 뒤 애플리케이션에서
 * 임계치 미달을 잘라내고, 그것을 "임계치를 넘는 최대 k건" 이라고 부른다. 이 동치는 유사도가 거리에
 * 대해 <b>단조 감소</b>일 때만 성립한다. 유사도 변환이 Platt scaling
 * {@code 1 / (1 + exp(A·distance + B))} 이고 계수 {@code A} 가 양수라 지금은 성립한다.
 *
 * <p><b>깨지는 경로는 하나다 — 계수 A 가 음수이거나 0 인 스펙이 추가되는 것.</b> 그러면 거리가 먼
 * 대상이 더 높은 유사도를 갖게 되어, 상위 k건 밖에 임계치를 넘는 대상이 남는다. 잘라낸 목록은
 * 조용히 불완전해지고 — 예외도, 로그도, 눈에 띄는 증상도 없다. 그래서 새 버전이 들어오는 순간
 * 여기서 멈춰야 한다.
 *
 * <p>이 검사는 {@link SimilarityCalculator} 의 정확한 값이 아니라 <b>순서</b>만 본다. 값 검증은
 * {@link SimilarityCalculatorTest} 가 한다.
 */
@DisplayName("UG-314: 유사도는 거리에 대해 단조 감소한다")
class SimilarityMonotonicityTest {

    /** 유사도 0.5 지점(A·d + B = 0)이 d ≈ 1.17 이라 이 구간이 실제 판정이 일어나는 대역이다. */
    private static final double 시작 = 0.001;
    private static final double 끝 = 5.0;
    private static final double 간격 = 0.01;

    @ParameterizedTest(name = "{0}")
    @EnumSource(DescriptorSpec.class)
    @DisplayName("계수 A 가 양수다 — 이게 단조성의 근거다")
    void 계수_A는_양수다(DescriptorSpec spec) {
        assertThat(spec.getDescriptorPlattSacleingA())
                .as("A 가 0 이하이면 거리가 멀수록 유사도가 커지거나 그대로다. 그러면 '거리 상위 "
                        + "k건 = 유사도 상위 k건' 이 깨지고, UG-314 의 후보 목록이 임계치를 넘는 "
                        + "대상을 조용히 빠뜨린다. 이 스펙을 지원하려면 후보 선별 방식을 먼저 "
                        + "다시 설계해야 한다 — DescriptorCustomRepository.oneToManyMatchTopK 참고")
                .isGreaterThan(0.0f);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DescriptorSpec.class)
    @DisplayName("거리가 멀어지면 유사도는 절대 올라가지 않는다")
    void 거리가_멀수록_유사도는_작아진다(DescriptorSpec spec) {
        BigDecimal 직전 = null;
        double 직전거리 = 0;

        for (double distance = 시작; distance <= 끝; distance += 간격) {
            BigDecimal 현재 = new BigDecimal(SimilarityCalculator.getSimilarityByDistance(distance, spec));

            if (직전 != null) {
                assertThat(현재)
                        .as("거리 %s → %s 로 멀어졌는데 유사도가 올라갔다 (%s → %s). 상위 k건 밖에 "
                                        + "임계치를 넘는 후보가 남는다는 뜻이다",
                                직전거리, distance, 직전, 현재)
                        .isLessThanOrEqualTo(직전);
            }

            직전 = 현재;
            직전거리 = distance;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DescriptorSpec.class)
    @DisplayName("판정이 실제로 갈리는 대역에서는 엄밀히 작아진다")
    void 판정_대역에서는_엄밀히_감소한다(DescriptorSpec spec) {
        // 위의 '올라가지 않는다' 만으로는 "항상 같은 값" 도 통과한다. 유사도가 상수가 되면
        // 임계치 판정 자체가 무의미해지므로 별도로 막는다.
        BigDecimal 가까움 = new BigDecimal(SimilarityCalculator.getSimilarityByDistance(0.9, spec));
        BigDecimal 중간 = new BigDecimal(SimilarityCalculator.getSimilarityByDistance(1.17, spec));
        BigDecimal 멈 = new BigDecimal(SimilarityCalculator.getSimilarityByDistance(1.5, spec));

        assertThat(가까움).isGreaterThan(중간);
        assertThat(중간).isGreaterThan(멈);
    }

    @Test
    @DisplayName("따름정리 — 거리순 목록에서 임계치 미달이 처음 나오면 그 뒤는 전부 미달이다")
    void 미달이_나오면_뒤는_전부_미달이다() {
        // 후보 목록 API 가 '자르기만 하면 되는' 이유가 이것이다. 미달을 하나 만났을 때
        // 뒤쪽을 더 뒤져 채울 대상이 남아 있다면 잘라내기는 틀린 구현이 된다.
        DescriptorSpec spec = DescriptorSpec.VERSION_59;
        double threshold = 0.85;

        // match-server 가 돌려주는 모양 그대로 — 거리 오름차순
        List<Double> 거리순 = new ArrayList<>();
        for (double d = 0.5; d <= 2.0; d += 0.05) {
            거리순.add(d);
        }

        boolean 미달을_봤다 = false;
        for (double distance : 거리순) {
            boolean 통과 = Double.parseDouble(
                    SimilarityCalculator.getSimilarityByDistance(distance, spec)) >= threshold;

            if (미달을_봤다) {
                assertThat(통과)
                        .as("거리 %s 에서 임계치를 다시 넘었다. 앞에서 미달을 이미 만났으므로 "
                                + "'미달을 만나면 멈춰도 된다' 는 전제가 깨진다", distance)
                        .isFalse();
            }
            if (!통과) {
                미달을_봤다 = true;
            }
        }

        assertThat(미달을_봤다)
                .as("검사 구간이 임계치를 한 번도 가로지르지 않았다 — 이 테스트가 공회전하고 있다")
                .isTrue();
    }
}

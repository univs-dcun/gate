package ai.univs.gate.modules.feature.application.input.face;

import java.math.BigDecimal;

/**
 * 특징점 기반 1:N 후보 목록 매칭 입력 (UG-314).
 *
 * <p>{@code thresholdPercent} 는 <b>클라이언트가 보낸 백분율 그대로</b>다 (0 초과 100 이하).
 * 도메인 스케일(0.0 ~ 1.0)로 바꾸는 것은 유스케이스가 하위 서비스를 부르는 시점이다 — 백분율은
 * gate 의 대외 표현이라 경계를 넘길 때 벗긴다.
 */
public record IdentifyCandidatesByDescriptorInput(
        Long accountId,
        String apiKey,
        String descriptor,
        BigDecimal thresholdPercent,
        int maxCandidates,
        String transactionUuid
) {
}

package ai.univs.gate.shared.exception;

import java.util.Set;

/**
 * 하위 서비스가 "내 쪽 오류다" 라고 알린 유형인가.
 *
 * <p>로그 분류({@link GlobalExceptionHandler}, {@code LoggingAspect})와 등록 이력의 발급 id 보존
 * ({@code FeatureHistory.fail}, UG-338)이 같은 기준을 쓴다. 도메인 엔티티가 웹 계층 핸들러에 기대지 않도록
 * 판정만 따로 둔다 (UG-338 반박 리뷰). 왜 HTTP 상태가 아니라 유형을 보는지는 핸들러의 설명을 참고할 것.
 */
public final class UpstreamErrorTypes {

    private static final Set<String> SERVER_ERROR_TYPES =
            Set.of("INTERNAL_SERVER_ERROR", "SERVER_ERROR", "INTERNAL_ERROR");

    private UpstreamErrorTypes() {
    }

    public static boolean isServerError(String type) {
        return type != null && SERVER_ERROR_TYPES.contains(type);
    }
}

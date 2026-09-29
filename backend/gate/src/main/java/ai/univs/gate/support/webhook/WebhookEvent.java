package ai.univs.gate.support.webhook;

/**
 * 웹훅 {@code event} 값 (UG-111). API 문서에 그대로 실리는 계약이다.
 *
 * <p>앞의 넷은 UG-111 이전부터 {@code MatchType} 이름으로 나가던 값이고, 데모 화면 실시간 알림
 * (Redis)도 같은 문자열을 쓴다. 바꾸면 데모 화면과 기존 수신 측이 함께 깨진다
 * ({@code WebhookEventTest} 가 지킨다).
 *
 * <p>descriptor 1:N 두 경로는 매칭 이력에 {@code IDENTIFY} 로 저장되지만 응답 구조가 이미지 기반
 * 1:N 과 달라서 이벤트 이름을 나눴다. 수신 측이 {@code event} 만 보고 {@code data} 구조를 알 수
 * 있어야 한다.
 */
public enum WebhookEvent {
    LIVENESS,
    VERIFY_ID,
    VERIFY_IMAGE,
    IDENTIFY,
    VERIFY_DESCRIPTOR,
    IDENTIFY_DESCRIPTOR,
    IDENTIFY_CANDIDATES_DESCRIPTOR,
}

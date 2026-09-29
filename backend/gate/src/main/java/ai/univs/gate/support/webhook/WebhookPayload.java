package ai.univs.gate.support.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * 웹훅 본문 (UG-111).
 *
 * <p>{@code event}·{@code data} 는 UG-111 이전부터 있던 필드라 이름을 바꾸지 않는다. 나머지는
 * 수신 측이 요청을 구분하고 중복을 거르는 데 필요한 값이다.
 *
 * @param eventId         전송 한 건의 고유 값. <b>재시도해도 같다</b> — 수신 측은 이 값으로 중복을
 *                        거른다. 헤더 {@code X-Gate-Event-Id} 에도 같은 값이 간다.
 * @param event           결과 종류 ({@link WebhookEvent} 이름)
 * @param source          {@code API} 또는 {@code DEMO} — 어느 경로로 호출됐는가
 * @param transactionUuid 요청의 거래 ID. 응답·매칭 이력의 값과 같다
 * @param occurredAt      결과가 만들어진 시각 (UTC, ISO-8601)
 * @param data            그 API 의 응답 {@code data} 와 같은 구조
 */
public record WebhookPayload(
        String eventId,
        String event,
        String source,
        String transactionUuid,
        Instant occurredAt,
        JsonNode data) {
}

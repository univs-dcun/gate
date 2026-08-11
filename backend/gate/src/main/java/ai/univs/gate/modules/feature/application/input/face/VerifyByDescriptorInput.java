package ai.univs.gate.modules.feature.application.input.face;

/**
 * 인자 순서는 {@code (accountId, apiKey)} 다 (UG-278).
 *
 * <p>다른 Input 레코드들과 순서가 뒤집혀 있었다. Long↔String 이라 자리를 바꾸면 컴파일이 실패하므로
 * 그 자체가 위험한 것은 아니지만, 읽는 사람이 매번 순서를 확인해야 했다. UG-274 는 {@code (Long, String)}
 * 형태에서 String 자리에 timezone 을 넣어도 컴파일이 통과해 4개월간 발견되지 않은 건이다 — 그런
 * 실수를 줄이려면 모든 Input 이 같은 순서를 갖는 편이 낫다.
 */
public record VerifyByDescriptorInput(
        Long accountId,
        String apiKey,
        String descriptor,
        String targetDescriptor,
        String transactionUuid
) {}
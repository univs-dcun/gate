package ai.univs.gate.facade.demo.application.dto;


public record DemoRedisPayload<T> (
        String event,
        String transactionUuid,
        T data
) {
}

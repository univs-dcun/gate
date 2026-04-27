package ai.univs.auth.application.event;

public record AccountCreatedEvent(
        Long accountId,
        String email
) {
}

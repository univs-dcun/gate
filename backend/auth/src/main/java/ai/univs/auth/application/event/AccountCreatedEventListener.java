package ai.univs.auth.application.event;

import ai.univs.auth.infrastructure.client.gate.GateClient;
import ai.univs.auth.infrastructure.client.gate.dto.InitCompanyFeignRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountCreatedEventListener {

    private final GateClient gateClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAccountCreated(AccountCreatedEvent event) {
        log.info("AccountCreatedEvent received: accountId={}, email={}", event.accountId(), event.email());
        try {
            var initCompanyRequest = new InitCompanyFeignRequestDTO(event.accountId(), event.email());
            gateClient.initCompany(initCompanyRequest);
            log.info("GateService company init succeeded: accountId={}", event.accountId());
        } catch (Exception e) {
            log.error("GateService company init failed: accountId={}, error={}", event.accountId(), e.getMessage(), e);
        }
    }
}

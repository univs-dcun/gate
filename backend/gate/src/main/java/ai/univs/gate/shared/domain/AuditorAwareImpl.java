package ai.univs.gate.shared.domain;

import ai.univs.gate.shared.auth.UserContext;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        UserContext context = UserContext.get();
        if (context != null && context.getAccountId() != null) {
            return Optional.of(context.getAccountIdAsLong());
        }

        // 0L : SYSTEM (qr 같은 비로그인 대상자의 요청일 때 사용)
        return Optional.of(0L);
    }
}
